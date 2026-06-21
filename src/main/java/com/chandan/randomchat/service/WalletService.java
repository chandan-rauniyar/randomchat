package com.chandan.randomchat.service;

import com.chandan.randomchat.dto.response.WalletStateResponse;
import com.chandan.randomchat.exception.*;
import com.chandan.randomchat.model.*;
import com.chandan.randomchat.model.enums.TransactionType;
import com.chandan.randomchat.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * WalletService — handles all coin and credit transactions.
 *
 * CRITICAL RULES enforced here:
 *   1. All balance changes happen in a SINGLE @Transactional method.
 *      Never split coin deduction and credit addition into separate calls.
 *   2. Always verify price from app_config — never trust client-sent price.
 *   3. coin_balance, match_credits, filter_credits NEVER go below 0.
 *   4. Every change logs a CoinTransaction row.
 *
 * The @Transactional annotation on each method means:
 *   - If anything fails (validation, DB write) → everything rolls back
 *   - User's balance is NEVER partially updated
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final UserRepository            userRepository;
    private final AppConfigRepository       appConfigRepository;
    private final CoinTransactionRepository coinTxRepository;
    private final UserService               userService;

    // =========================================================================
    // Buy credits with coins
    // =========================================================================

    /**
     * Purchase a credit pack using coins.
     *
     * purchaseType values: MATCH, GENDER_FILTER, COUNTRY_FILTER, BUNDLE_FILTER
     *
     * Steps:
     *   1. Load pricing from app_config (server-side — never trust client)
     *   2. Verify client-sent coinCost matches server price (prevent 0-coin exploit)
     *   3. Check user has enough coins
     *   4. Deduct coins + add credits + log transaction (all in one DB transaction)
     *   5. Return updated user
     */
    @Transactional
    public User buyCredits(UUID userId, String appId,
                           String purchaseType, int clientCoinCost) {

        // Load user with row lock to prevent race condition (two requests at once)
        User user = userService.loadUser(userId, appId);

        // Get server-side price — NEVER use clientCoinCost directly
        PricingInfo pricing = getPricing(appId, purchaseType);

        // Validate client isn't sending a tampered price
        if (clientCoinCost != pricing.coinCost) {
            throw new PriceMismatchException(pricing.coinCost, clientCoinCost);
        }

        // Check sufficient coins
        if (user.getCoinBalance() < pricing.coinCost) {
            throw new InsufficientCoinsException(pricing.coinCost, user.getCoinBalance());
        }

        // Apply the transaction
        CoinTransaction tx = applyCredits(user, appId, purchaseType, pricing);
        coinTxRepository.save(tx);

        User saved = userRepository.save(user);
        log.info("Credits purchased: type={} user={} coinsSpent={}",
                purchaseType, userId, pricing.coinCost);
        return saved;
    }

    // =========================================================================
    // Deduct credits on accepted match (called by Phase 2 MatchService)
    // =========================================================================

    /**
     * Deduct 1 match credit (and filter credits if active) when both users accept.
     *
     * Called inside the match-accept transaction in MatchService.
     * Both users are passed together to allow locking in consistent order
     * (lower UUID first) to prevent deadlock when two rows are locked.
     *
     * @param user      the user whose credits to deduct
     * @param sessionId the session UUID for audit reference
     */
    @Transactional
    public void deductMatchCredits(User user, String sessionId) {

        if (user.getMatchCredits() <= 0) {
            throw new InsufficientCreditsException("MATCH");
        }

        // Deduct match credit
        user.setMatchCredits(user.getMatchCredits() - 1);
        CoinTransaction matchTx = CoinTransaction.matchDeduct(user, sessionId);
        coinTxRepository.save(matchTx);

        // Deduct gender filter credit if active
        if (user.hasActiveGenderFilter()) {
            int before = user.getFilterCreditsGender();
            user.setFilterCreditsGender(before - 1);

            CoinTransaction genderTx = CoinTransaction.builder()
                    .appId(user.getAppId()).user(user)
                    .transactionType(TransactionType.GENDER_FILTER_DEDUCT)
                    .coinAmount(0)
                    .coinBalanceBefore(user.getCoinBalance()).coinBalanceAfter(user.getCoinBalance())
                    .matchCreditsBefore(user.getMatchCredits()).matchCreditsAfter(user.getMatchCredits())
                    .genderFilterDelta(-1).genderFilterBefore(before).genderFilterAfter(before - 1)
                    .countryFilterBefore(user.getFilterCreditsCountry())
                    .countryFilterAfter(user.getFilterCreditsCountry())
                    .referenceId(sessionId).referenceType("SESSION")
                    .build();
            coinTxRepository.save(genderTx);
        }

        // Deduct country filter credit if active
        if (user.hasActiveCountryFilter()) {
            int before = user.getFilterCreditsCountry();
            user.setFilterCreditsCountry(before - 1);

            CoinTransaction countryTx = CoinTransaction.builder()
                    .appId(user.getAppId()).user(user)
                    .transactionType(TransactionType.COUNTRY_FILTER_DEDUCT)
                    .coinAmount(0)
                    .coinBalanceBefore(user.getCoinBalance()).coinBalanceAfter(user.getCoinBalance())
                    .matchCreditsBefore(user.getMatchCredits()).matchCreditsAfter(user.getMatchCredits())
                    .genderFilterBefore(user.getFilterCreditsGender())
                    .genderFilterAfter(user.getFilterCreditsGender())
                    .countryFilterDelta(-1).countryFilterBefore(before).countryFilterAfter(before - 1)
                    .referenceId(sessionId).referenceType("SESSION")
                    .build();
            coinTxRepository.save(countryTx);
        }

        // Clear filters if they hit 0 (DB trigger also does this — double safety)
        user.clearExpiredFilters();
        userRepository.save(user);
    }

    // =========================================================================
    // Get wallet state + pricing (for Shop screen)
    // =========================================================================

    public WalletStateInfo getWalletState(UUID userId, String appId, int adsWatchedToday) {
        User user = userService.loadUser(userId, appId);
        int dailyCap = getConfigInt(appId, "ad_daily_cap", 10);
        return new WalletStateInfo(user, buildPricingInfo(appId),
                getConfigInt(appId, "ad_coins_reward", 3),
                getConfigInt(appId, "ad_first_daily_bonus", 5),
                adsWatchedToday, dailyCap);
    }

    // =========================================================================
    // Pricing helpers
    // =========================================================================

    private PricingInfo getPricing(String appId, String purchaseType) {
        return switch (purchaseType) {
            case "MATCH" -> new PricingInfo(
                    getConfigInt(appId, "price_match_pack", 10),
                    getConfigInt(appId, "match_credits_per_pack", 20),
                    0, 0
            );
            case "GENDER_FILTER" -> new PricingInfo(
                    getConfigInt(appId, "price_gender_filter_pack", 10),
                    0,
                    getConfigInt(appId, "gender_filter_per_pack", 10),
                    0
            );
            case "COUNTRY_FILTER" -> new PricingInfo(
                    getConfigInt(appId, "price_country_filter_pack", 10),
                    0, 0,
                    getConfigInt(appId, "country_filter_per_pack", 10)
            );
            case "BUNDLE_FILTER" -> new PricingInfo(
                    getConfigInt(appId, "price_bundle_filter_pack", 18),
                    0,
                    getConfigInt(appId, "bundle_gender_credits", 10),
                    getConfigInt(appId, "bundle_country_credits", 10)
            );
            default -> throw new AppException("INVALID_PURCHASE_TYPE",
                    "Unknown purchase type: " + purchaseType, HttpStatus.BAD_REQUEST) {};
        };
    }

    private CoinTransaction applyCredits(User user, String appId,
                                         String purchaseType, PricingInfo pricing) {
        int coinBefore        = user.getCoinBalance();
        int matchBefore       = user.getMatchCredits();
        int genderBefore      = user.getFilterCreditsGender();
        int countryBefore     = user.getFilterCreditsCountry();

        // Deduct coins
        user.setCoinBalance(coinBefore - pricing.coinCost);

        // Add credits
        user.setMatchCredits(matchBefore + pricing.matchCredits);
        user.setFilterCreditsGender(genderBefore + pricing.genderFilterCredits);
        user.setFilterCreditsCountry(countryBefore + pricing.countryFilterCredits);

        TransactionType txType = switch (purchaseType) {
            case "MATCH"          -> TransactionType.MATCH_CREDIT_PURCHASE;
            case "GENDER_FILTER"  -> TransactionType.GENDER_FILTER_PURCHASE;
            case "COUNTRY_FILTER" -> TransactionType.COUNTRY_FILTER_PURCHASE;
            case "BUNDLE_FILTER"  -> TransactionType.BUNDLE_FILTER_PURCHASE;
            default               -> TransactionType.MATCH_CREDIT_PURCHASE;
        };

        return CoinTransaction.builder()
                .appId(appId).user(user)
                .transactionType(txType)
                .coinAmount(-pricing.coinCost)
                .coinBalanceBefore(coinBefore).coinBalanceAfter(user.getCoinBalance())
                .matchCreditsDelta(pricing.matchCredits)
                .matchCreditsBefore(matchBefore).matchCreditsAfter(user.getMatchCredits())
                .genderFilterDelta(pricing.genderFilterCredits)
                .genderFilterBefore(genderBefore).genderFilterAfter(user.getFilterCreditsGender())
                .countryFilterDelta(pricing.countryFilterCredits)
                .countryFilterBefore(countryBefore).countryFilterAfter(user.getFilterCreditsCountry())
                .referenceType("WALLET")
                .build();
    }

    private WalletStateResponse.PricingInfo buildPricingInfo(String appId) {
        return WalletStateResponse.PricingInfo.builder()
                .matchPack(WalletStateResponse.PricingInfo.PackInfo.builder()
                        .coinCost(getConfigInt(appId, "price_match_pack", 10))
                        .creditsGained(getConfigInt(appId, "match_credits_per_pack", 20))
                        .build())
                .genderFilterPack(WalletStateResponse.PricingInfo.PackInfo.builder()
                        .coinCost(getConfigInt(appId, "price_gender_filter_pack", 10))
                        .creditsGained(getConfigInt(appId, "gender_filter_per_pack", 10))
                        .build())
                .countryFilterPack(WalletStateResponse.PricingInfo.PackInfo.builder()
                        .coinCost(getConfigInt(appId, "price_country_filter_pack", 10))
                        .creditsGained(getConfigInt(appId, "country_filter_per_pack", 10))
                        .build())
                .bundleFilterPack(WalletStateResponse.PricingInfo.BundlePackInfo.builder()
                        .coinCost(getConfigInt(appId, "price_bundle_filter_pack", 18))
                        .genderCredits(getConfigInt(appId, "bundle_gender_credits", 10))
                        .countryCredits(getConfigInt(appId, "bundle_country_credits", 10))
                        .build())
                .build();
    }

    private int getConfigInt(String appId, String key, int defaultValue) {
        return appConfigRepository.findByAppIdAndConfigKey(appId, key)
                .map(AppConfig::getValueAsInt)
                .orElse(defaultValue);
    }

    // Inner records for internal use
    private record PricingInfo(int coinCost, int matchCredits,
                               int genderFilterCredits, int countryFilterCredits) {}
    public record WalletStateInfo(User user, WalletStateResponse.PricingInfo pricing,
                                  int adCoinsReward, int firstDailyBonus,
                                  int adsWatchedToday, int dailyCap) {}
}