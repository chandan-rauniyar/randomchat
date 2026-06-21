package com.chandan.randomchat.service;

import com.chandan.randomchat.exception.UserBannedException;
import com.chandan.randomchat.exception.UserNotFoundException;
import com.chandan.randomchat.model.Ban;
import com.chandan.randomchat.model.Report;
import com.chandan.randomchat.model.User;
import com.chandan.randomchat.model.enums.BanSource;
import com.chandan.randomchat.model.enums.BanType;
import com.chandan.randomchat.model.enums.ReportStatus;
import com.chandan.randomchat.repository.BanRepository;
import com.chandan.randomchat.repository.ReportRepository;
import com.chandan.randomchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * BanService — issue bans, lift bans, auto-ban from report threshold.
 *
 * Ban lifecycle:
 *   1. Admin manually bans → issueBan()
 *   2. System auto-bans after N reports → checkAndAutoBan()
 *   3. Temp ban expires → BanExpiryJob calls liftBan()
 *   4. Admin lifts manually → liftBan()
 *
 * Two tables always updated atomically:
 *   bans table       → audit history
 *   users.is_banned  → live gate checked on every /init
 *
 * Auto-ban threshold: 3 pending/actioned reports → 48h temporary ban.
 * Configurable — change AUTO_BAN_THRESHOLD and AUTO_BAN_HOURS here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BanService {

    private static final int AUTO_BAN_THRESHOLD = 3;    // reports needed to trigger auto-ban
    private static final int AUTO_BAN_HOURS     = 48;   // hours for auto-ban duration

    private final BanRepository    banRepository;
    private final UserRepository   userRepository;
    private final ReportRepository reportRepository;

    // =========================================================================
    // Issue a ban (admin action)
    // =========================================================================

    /**
     * Issue a ban on a user.
     *
     * @param targetUser   the user to ban
     * @param banType      TEMPORARY or PERMANENT
     * @param reason       human-readable reason
     * @param durationHours hours for temp ban (ignored if PERMANENT)
     * @param bannedBy     admin username or "SYSTEM"
     * @param reportId     optional: which report triggered this (null for admin direct ban)
     */
    @Transactional
    public Ban issueBan(User targetUser, BanType banType, String reason,
                        int durationHours, String bannedBy, UUID reportId) {

        // Check if already banned — don't stack bans, extend/replace instead
        banRepository.findFirstByUserIdAndIsLiftedFalseOrderByBannedAtDesc(targetUser.getId())
                .ifPresent(existing -> {
                    // Lift existing ban before issuing new one
                    existing.lift(bannedBy, "Replaced by new ban");
                    banRepository.save(existing);
                });

        Instant expiresAt = banType == BanType.TEMPORARY
                ? Instant.now().plus(durationHours, ChronoUnit.HOURS)
                : null;

        // Create ban audit record
        Ban ban = Ban.builder()
                .user(targetUser)
                .appId(targetUser.getAppId())
                .banType(banType)
                .banSource(bannedBy.equals("SYSTEM") ? BanSource.AUTO_REPORT : BanSource.ADMIN)
                .reason(reason)
                .bannedBy(bannedBy)
                .expiresAt(expiresAt)
                .reportId(reportId)
                .isLifted(false)
                .build();
        banRepository.save(ban);

        // Update live ban state on user
        targetUser.setIsBanned(true);
        targetUser.setBanReason(reason);
        targetUser.setBanExpiresAt(expiresAt);
        userRepository.save(targetUser);

        log.info("User {} banned by {} — type={} reason='{}' expires={}",
                targetUser.getId(), bannedBy, banType, reason, expiresAt);

        return ban;
    }

    // =========================================================================
    // Lift a ban (admin action or auto-expiry)
    // =========================================================================

    /**
     * Lift an active ban.
     * Called by: admin action OR BanExpiryJob (automated expiry).
     *
     * @param userId      user to unban
     * @param appId       for user lookup
     * @param liftedBy    admin username or "SYSTEM"
     * @param liftReason  why it was lifted
     */
    @Transactional
    public void liftBan(UUID userId, String appId, String liftedBy, String liftReason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Find and lift the active ban record
        banRepository.findFirstByUserIdAndIsLiftedFalseOrderByBannedAtDesc(userId)
                .ifPresent(ban -> {
                    ban.lift(liftedBy, liftReason);
                    banRepository.save(ban);
                });

        // Clear ban state on user
        user.setIsBanned(false);
        user.setBanReason(null);
        user.setBanExpiresAt(null);
        userRepository.save(user);

        log.info("Ban lifted for user {} by {}", userId, liftedBy);
    }

    // =========================================================================
    // Auto-ban check — called after every new report submission
    // =========================================================================

    /**
     * Check if a reported user has hit the auto-ban threshold.
     * Called by ReportService immediately after inserting a new report.
     *
     * If reportCount >= AUTO_BAN_THRESHOLD → issue 48h temporary ban automatically.
     *
     * @param reportedUser the user who was reported
     * @param latestReport the report just submitted (for reference)
     */
    @Transactional
    public void checkAndAutoBan(User reportedUser, Report latestReport) {
        // Skip if already banned
        if (reportedUser.getIsBanned()) return;

        int reportCount = reportRepository.countByReportedIdAndAppIdAndStatusIn(
                reportedUser.getId(),
                reportedUser.getAppId(),
                List.of(ReportStatus.PENDING, ReportStatus.ACTIONED)
        );

        if (reportCount >= AUTO_BAN_THRESHOLD) {
            String reason = "Auto-banned: " + reportCount +
                    " reports received. Latest: " + latestReport.getReason().name();

            issueBan(reportedUser, BanType.TEMPORARY, reason,
                    AUTO_BAN_HOURS, "SYSTEM", latestReport.getId());

            log.info("Auto-ban issued for user {} after {} reports",
                    reportedUser.getId(), reportCount);
        }
    }

    // =========================================================================
    // Check ban status (used in /init and /status)
    // =========================================================================

    /**
     * Validate user is not banned. Throws UserBannedException if banned.
     * Also handles expired temp bans — clears them if found.
     */
    @Transactional
    public void assertNotBanned(User user) {
        if (!user.getIsBanned()) return;

        // Check if temporary ban has expired (edge case: scheduler hasn't run yet)
        if (user.isBanExpired()) {
            liftBan(user.getId(), user.getAppId(), "SYSTEM", "Auto-expired on access");
            return; // no longer banned after lift
        }

        throw new UserBannedException(user.getBanReason(), user.getBanExpiresAt());
    }
}