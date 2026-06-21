package com.chandan.randomchat.service;


import com.chandan.randomchat.exception.AdDailyLimitException;
import com.chandan.randomchat.model.AdView;
import com.chandan.randomchat.model.CoinTransaction;
import com.chandan.randomchat.model.User;
import com.chandan.randomchat.model.enums.AdType;
import com.chandan.randomchat.repository.AdViewRepository;
import com.chandan.randomchat.repository.AppConfigRepository;
import com.chandan.randomchat.repository.CoinTransactionRepository;
import com.chandan.randomchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * AdService — handle rewarded ad completions.
 *
 * Flow:
 *   1. Android completes watching a rewarded ad (AdMob fires callback)
 *   2. Android calls POST /api/ads/reward with verification token
 *   3. Backend checks daily cap (max 10 ads/day from app_config)
 *   4. Backend verifies token with AdMob server-side verification (Phase 2+ — skip for now)
 *   5. Credit coins (first ad of day gets bonus, rest get standard amount)
 *   6. Log to ad_views + coin_transactions
 *   7. Return updated wallet
 *
 * Daily cap enforcement is the CRITICAL check — run before anything else.
 * Uses idx_adviews_daily_cap index — sub-millisecond.
 *
 * Phase 1: skip server-side AdMob verification (mark verified=TRUE immediately).
 * Phase 2: integrate AdMob SSV (server-side verification) callback endpoint.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdService {

    private final AdViewRepository adViewRepository;
    private final CoinTransactionRepository coinTxRepository;
    private final UserRepository userRepository;
    private final AppConfigRepository appConfigRepository;
    private final UserService               userService;

    // =========================================================================
    // Record a completed rewarded ad and credit coins
    // =========================================================================

    /**
     * Called when Android reports a completed rewarded ad.
     *
     * @param userId            user who watched the ad
     * @param appId             app context
     * @param adUnitId          AdMob ad unit ID
     * @param verificationToken server-side verification token from AdMob
     *                          (Phase 1: pass null — verification added later)
     * @return updated User with new coin balance
     */
    @Transactional
    public User recordAdReward(UUID userId, String appId,
                               String adUnitId, String verificationToken) {

        User user = userService.loadUser(userId, appId);

        // 1. Check daily cap FIRST — before creating any rows
        int dailyCap  = getConfigInt(appId, "ad_daily_cap", 10);
        int todayCount = adViewRepository.countByUserIdAndAppIdAndViewDateAndVerifiedTrue(
                userId, appId, LocalDate.now());

        if (todayCount >= dailyCap) {
            throw new AdDailyLimitException(dailyCap);
        }

        // 2. Determine coin reward amount
        boolean isFirstOfDay = !adViewRepository.existsByUserIdAndAppIdAndViewDateAndVerifiedTrue(
                userId, appId, LocalDate.now());

        int coinsToReward = isFirstOfDay
                ? getConfigInt(appId, "ad_first_daily_bonus", 5)
                : getConfigInt(appId, "ad_coins_reward", 3);

        // 3. Create AdView record
        AdView adView = AdView.builder()
                .appId(appId)
                .user(user)
                .adType(AdType.REWARDED)
                .adUnitId(adUnitId)
                .isFirstOfDay(isFirstOfDay)
                .coinsRewarded(coinsToReward)
                .verified(true)              // Phase 1: trust client. Phase 2: verify with AdMob
                .verifiedAt(Instant.now())
                .verificationToken(verificationToken)
                .viewDate(LocalDate.now())
                .build();

        // 4. Credit coins to user
        int coinsBefore = user.getCoinBalance();
        user.setCoinBalance(coinsBefore + coinsToReward);

        // 5. Create coin transaction record BEFORE saving adView
        //    (so we can link them)
        CoinTransaction tx = CoinTransaction.adReward(user, coinsToReward,
                null, isFirstOfDay);   // adView ID filled in after save
        CoinTransaction savedTx = coinTxRepository.save(tx);

        // 6. Link ad view to its coin transaction and save
        adView.setCoinTransaction(savedTx);
        AdView savedAdView = adViewRepository.save(adView);

        // 7. Update the transaction reference_id with the ad view ID
        savedTx.setReferenceId(savedAdView.getId().toString());
        coinTxRepository.save(savedTx);

        // 8. Save updated user balance
        User saved = userRepository.save(user);

        log.info("Ad reward: user={} coins=+{} isFirstOfDay={} todayTotal={}/{}",
                userId, coinsToReward, isFirstOfDay, todayCount + 1, dailyCap);

        return saved;
    }

    // =========================================================================
    // Check how many ads user has watched today (for Shop screen display)
    // =========================================================================

    public int getAdsWatchedToday(UUID userId, String appId) {
        return adViewRepository.countByUserIdAndAppIdAndViewDateAndVerifiedTrue(
                userId, appId, LocalDate.now());
    }

    // =========================================================================
    // Config helper
    // =========================================================================

    private int getConfigInt(String appId, String key, int defaultValue) {
        return appConfigRepository.findByAppIdAndConfigKey(appId, key)
                .map(c -> Integer.parseInt(c.getConfigValue()))
                .orElse(defaultValue);
    }
}
