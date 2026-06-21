package com.chandan.randomchat.service;

import com.chandan.randomchat.exception.InsufficientCreditsException;
import com.chandan.randomchat.exception.InvalidTokenException;
import com.chandan.randomchat.exception.UserNotFoundException;
import com.chandan.randomchat.model.AppConfig;
import com.chandan.randomchat.model.CoinTransaction;
import com.chandan.randomchat.model.User;
import com.chandan.randomchat.model.enums.GenderType;
import com.chandan.randomchat.repository.AppConfigRepository;
import com.chandan.randomchat.repository.CoinTransactionRepository;
import com.chandan.randomchat.repository.UserRepository;
import com.chandan.randomchat.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository            userRepository;
    private final AppConfigRepository       appConfigRepository;
    private final CoinTransactionRepository coinTxRepository;
    private final JwtService                jwtService;

    private static final Random RANDOM = new Random();

    // =========================================================================
    // Init — called on every app launch
    // =========================================================================

    @Transactional
    public InitResult initUser(String deviceId, String appId, String countryCode) {

        String deviceIdHash = hashDeviceId(deviceId, appId);

        return userRepository
                .findByDeviceIdHashAndAppIdAndIsDeletedFalse(deviceIdHash, appId)
                .map(user -> {
                    // Returning user — refresh country, clear expired filters, new JWT
                    if (countryCode != null && !countryCode.isBlank()) {
                        user.setCountryCode(countryCode.toUpperCase());
                    }
                    if (user.clearExpiredFilters()) {
                        log.debug("Auto-cleared expired filters for user {}", user.getId());
                    }
                    user.setLastSeenAt(Instant.now());
                    User saved = userRepository.save(user);
                    String jwt = jwtService.generateToken(saved.getId(), appId);
                    return new InitResult(saved, jwt, false);
                })
                .orElseGet(() -> {
                    // ── New user OR reinstall after account deletion ──────────────
                    // The old deleted row (if any) still exists in the DB but
                    // has a blanked device_id_hash (see deleteAccount() below),
                    // so the UNIQUE constraint on (device_id_hash, app_id) is free.
                    // We can safely INSERT a brand new row here.
                    // ─────────────────────────────────────────────────────────────
                    User newUser = createNewUser(deviceIdHash, appId, countryCode);
                    User saved   = userRepository.save(newUser);
                    creditSignupBonus(saved, appId);
                    saved = userRepository.save(saved);
                    String jwt = jwtService.generateToken(saved.getId(), appId);
                    log.info("New user created: {} on app {}", saved.getUsername(), appId);
                    return new InitResult(saved, jwt, true);
                });
    }

    // =========================================================================
    // Profile update
    // =========================================================================

    @Transactional
    public User updateProfile(UUID userId, String appId,
                              GenderType gender, String countryCode) {
        User user = loadUser(userId, appId);
        if (gender != null)      user.setGender(gender);
        if (countryCode != null) user.setCountryCode(countryCode.toUpperCase());
        return userRepository.save(user);
    }

    // =========================================================================
    // Filter update
    // =========================================================================

    @Transactional
    public User updateFilter(UUID userId, String appId,
                             GenderType genderFilter, String countryFilter) {
        User user = loadUser(userId, appId);

        if (genderFilter != null && user.getFilterCreditsGender() <= 0) {
            throw new InsufficientCreditsException("GENDER_FILTER");
        }
        if (countryFilter != null && user.getFilterCreditsCountry() <= 0) {
            throw new InsufficientCreditsException("COUNTRY_FILTER");
        }

        user.setActiveGenderFilter(genderFilter);
        user.setActiveCountryFilter(
                countryFilter != null ? countryFilter.toUpperCase() : null);
        return userRepository.save(user);
    }

    // =========================================================================
    // Account deletion — THE FIX IS HERE
    // =========================================================================

    /**
     * Soft delete a user account.
     *
     * THE FIX:
     *   Problem: after soft delete, the row kept device_id_hash + app_id intact.
     *   When the same device reinstalled the app, initUser() correctly found
     *   no ACTIVE user (is_deleted = FALSE), so it tried to INSERT a new row.
     *   But the old deleted row still had the same (device_id_hash, app_id),
     *   violating the UNIQUE constraint "uq_users_device_app" → 500 crash.
     *
     *   Fix: on deletion, OVERWRITE device_id_hash with a unique tombstone value:
     *     "DELETED_" + userId + "_" + epochMillis
     *   This frees the unique slot immediately.
     *   The old row is kept for admin audit (coin history, session history, bans)
     *   but it can never block a reinstall again.
     *
     *   No schema change needed — device_id_hash is VARCHAR(64), the tombstone
     *   fits easily and is clearly identifiable in admin queries.
     */
    @Transactional
    public void deleteAccount(UUID userId, String appId) {
        User user = loadUser(userId, appId);

        // Build a unique tombstone that frees the unique constraint slot
        // but keeps the row identifiable as a deleted record for admin
        String tombstone = "DELETED_" + userId + "_" + Instant.now().toEpochMilli();

        user.setIsDeleted(true);
        user.setDeletedAt(Instant.now());
        user.setDeviceIdHash(tombstone);     // ← free the unique slot
        user.setMatchCredits(0);
        user.setFilterCreditsGender(0);
        user.setFilterCreditsCountry(0);
        user.setActiveGenderFilter(null);
        user.setActiveCountryFilter(null);

        userRepository.save(user);
        log.info("User {} on app {} soft deleted, device_id_hash cleared", userId, appId);
    }

    // =========================================================================
    // Load user — two variants
    // =========================================================================

    /**
     * Load the CALLING user — validates they belong to the same app as the JWT.
     * Use for: heartbeat, buy credits, update profile, get status, etc.
     */
    @Transactional
    public User loadUser(UUID userId, String appId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.getIsDeleted()) {
            throw new UserNotFoundException(userId);
        }

        // Security: JWT's appId must match user's actual appId
        if (!user.getAppId().equals(appId)) {
            throw new InvalidTokenException("Token app mismatch");
        }

        if (user.clearExpiredFilters()) {
            userRepository.save(user);
        }

        return user;
    }

    /**
     * Load ANY user by UUID — no app ownership check.
     * Use for: loading reported users, ban targets, cross-app lookups.
     * Cross-app matching means reported user may be on a different app.
     */
    @Transactional
    public User loadAnyUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.getIsDeleted()) {
            throw new UserNotFoundException(userId);
        }

        return user;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private User createNewUser(String deviceIdHash, String appId, String countryCode) {
        int signupBonus = getConfigInt(appId, "signup_bonus_coins", 10);

        return User.builder()
                .appId(appId)
                .deviceIdHash(deviceIdHash)
                .username(generateUniqueUsername(appId))
                .countryCode(countryCode != null ? countryCode.toUpperCase() : "XX")
                .coinBalance(signupBonus)
                .matchCredits(0)
                .filterCreditsGender(0)
                .filterCreditsCountry(0)
                .isBanned(false)
                .isDeleted(false)
                .lastSeenAt(Instant.now())
                .build();
    }

    private void creditSignupBonus(User user, String appId) {
        int bonus = getConfigInt(appId, "signup_bonus_coins", 10);
        CoinTransaction tx = CoinTransaction.signupBonus(user, bonus);
        coinTxRepository.save(tx);
    }

    public String hashDeviceId(String deviceId, String appId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    (deviceId + ":" + appId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String generateUniqueUsername(String appId) {
        String username;
        int attempts = 0;
        do {
            username = "User_" + (1000 + RANDOM.nextInt(9000));
            if (++attempts > 100) {
                username = "User_" + (10000 + RANDOM.nextInt(90000));
            }
        } while (userRepository.existsByUsernameAndAppId(username, appId));
        return username;
    }

    private int getConfigInt(String appId, String key, int defaultValue) {
        return appConfigRepository.findByAppIdAndConfigKey(appId, key)
                .map(AppConfig::getValueAsInt)
                .orElse(defaultValue);
    }

    public record InitResult(User user, String jwt, boolean isNewUser) {}
}