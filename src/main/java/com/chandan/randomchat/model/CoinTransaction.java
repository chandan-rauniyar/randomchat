package com.chandan.randomchat.model;

import com.chandan.randomchat.model.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "coin_transactions",
        indexes = {
                @Index(name = "idx_txn_user_date",      columnList = "user_id, created_at DESC"),
                @Index(name = "idx_txn_app_type_date",  columnList = "app_id, transaction_type, created_at DESC"),
                @Index(name = "idx_txn_app_date",       columnList = "app_id, created_at DESC"),
                @Index(name = "idx_txn_reference",      columnList = "reference_id, reference_type"),
                @Index(name = "idx_txn_purchases",      columnList = "app_id, transaction_type, created_at DESC")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "user")
public class CoinTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "app_id", nullable = false, length = 50)
    private String appId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            referencedColumnName = "id",
            nullable = true,
            foreignKey = @ForeignKey(name = "fk_coin_transactions_user")
    )
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 40)
    private TransactionType transactionType;

    @Column(name = "coin_amount", nullable = false)
    @Builder.Default
    private Integer coinAmount = 0;

    @Column(name = "coin_balance_before", nullable = false)
    private Integer coinBalanceBefore;

    @Column(name = "coin_balance_after", nullable = false)
    private Integer coinBalanceAfter;

    @Column(name = "match_credits_delta", nullable = false)
    @Builder.Default
    private Integer matchCreditsDelta = 0;

    @Column(name = "match_credits_before", nullable = false)
    private Integer matchCreditsBefore;

    @Column(name = "match_credits_after", nullable = false)
    private Integer matchCreditsAfter;

    @Column(name = "gender_filter_delta", nullable = false)
    @Builder.Default
    private Integer genderFilterDelta = 0;

    @Column(name = "gender_filter_before", nullable = false)
    private Integer genderFilterBefore;

    @Column(name = "gender_filter_after", nullable = false)
    private Integer genderFilterAfter;

    @Column(name = "country_filter_delta", nullable = false)
    @Builder.Default
    private Integer countryFilterDelta = 0;

    @Column(name = "country_filter_before", nullable = false)
    private Integer countryFilterBefore;

    @Column(name = "country_filter_after", nullable = false)
    private Integer countryFilterAfter;

    @Column(name = "reference_id", length = 500)
    private String referenceId;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static CoinTransaction signupBonus(User user, int bonusCoins) {
        return CoinTransaction.builder()
                .appId(user.getAppId())
                .user(user)
                .transactionType(TransactionType.SIGNUP_BONUS)
                .coinAmount(bonusCoins)
                .coinBalanceBefore(0)
                .coinBalanceAfter(bonusCoins)
                .matchCreditsBefore(0).matchCreditsAfter(0)
                .genderFilterBefore(0).genderFilterAfter(0)
                .countryFilterBefore(0).countryFilterAfter(0)
                .referenceType("SYSTEM")
                .build();
    }

    public static CoinTransaction adReward(User user, int coinsRewarded,
                                           String adViewId, boolean isFirstOfDay) {
        return CoinTransaction.builder()
                .appId(user.getAppId())
                .user(user)
                .transactionType(isFirstOfDay
                        ? TransactionType.AD_REWARD_DAILY_BONUS
                        : TransactionType.AD_REWARD)
                .coinAmount(coinsRewarded)
                .coinBalanceBefore(user.getCoinBalance())
                .coinBalanceAfter(user.getCoinBalance() + coinsRewarded)
                .matchCreditsBefore(user.getMatchCredits()).matchCreditsAfter(user.getMatchCredits())
                .genderFilterBefore(user.getFilterCreditsGender()).genderFilterAfter(user.getFilterCreditsGender())
                .countryFilterBefore(user.getFilterCreditsCountry()).countryFilterAfter(user.getFilterCreditsCountry())
                .referenceId(adViewId)
                .referenceType("AD_VIEW")
                .build();
    }

    public static CoinTransaction matchCreditPurchase(User user, int coinCost, int creditsGained) {
        return CoinTransaction.builder()
                .appId(user.getAppId())
                .user(user)
                .transactionType(TransactionType.MATCH_CREDIT_PURCHASE)
                .coinAmount(-coinCost)
                .coinBalanceBefore(user.getCoinBalance())
                .coinBalanceAfter(user.getCoinBalance() - coinCost)
                .matchCreditsDelta(creditsGained)
                .matchCreditsBefore(user.getMatchCredits())
                .matchCreditsAfter(user.getMatchCredits() + creditsGained)
                .genderFilterBefore(user.getFilterCreditsGender()).genderFilterAfter(user.getFilterCreditsGender())
                .countryFilterBefore(user.getFilterCreditsCountry()).countryFilterAfter(user.getFilterCreditsCountry())
                .referenceType("WALLET")
                .build();
    }

    public static CoinTransaction matchDeduct(User user, String sessionId) {
        return CoinTransaction.builder()
                .appId(user.getAppId())
                .user(user)
                .transactionType(TransactionType.MATCH_DEDUCT)
                .coinAmount(0)
                .coinBalanceBefore(user.getCoinBalance()).coinBalanceAfter(user.getCoinBalance())
                .matchCreditsDelta(-1)
                .matchCreditsBefore(user.getMatchCredits())
                .matchCreditsAfter(user.getMatchCredits() - 1)
                .genderFilterBefore(user.getFilterCreditsGender()).genderFilterAfter(user.getFilterCreditsGender())
                .countryFilterBefore(user.getFilterCreditsCountry()).countryFilterAfter(user.getFilterCreditsCountry())
                .referenceId(sessionId)
                .referenceType("SESSION")
                .build();
    }
}