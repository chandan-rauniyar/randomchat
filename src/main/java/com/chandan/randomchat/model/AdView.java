package com.chandan.randomchat.model;

import com.chandan.randomchat.model.enums.AdType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "ad_views",
        indexes = {
                @Index(name = "idx_adviews_daily_cap",    columnList = "user_id, app_id, view_date"),
                @Index(name = "idx_adviews_app_date",     columnList = "app_id, view_date DESC, ad_type"),
                @Index(name = "idx_adviews_user_date",    columnList = "user_id, viewed_at DESC"),
                @Index(name = "idx_adviews_unverified",   columnList = "app_id, viewed_at DESC"),
                @Index(name = "idx_adviews_daily_bonus",  columnList = "app_id, view_date")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"user", "coinTransaction"})
public class AdView {

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
            foreignKey = @ForeignKey(name = "fk_ad_views_user")
    )
    private User user;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "ad_type", nullable = false, length = 20, columnDefinition = "ad_type")
    @Builder.Default
    private AdType adType = AdType.REWARDED;

    @Column(name = "ad_unit_id", length = 255)
    private String adUnitId;

    @Column(name = "ad_network", nullable = false, length = 50)
    @Builder.Default
    private String adNetwork = "ADMOB";

    @Column(name = "coins_rewarded", nullable = false)
    @Builder.Default
    private Integer coinsRewarded = 0;

    @Column(name = "is_first_of_day", nullable = false)
    @Builder.Default
    private Boolean isFirstOfDay = false;

    @Column(name = "verified", nullable = false)
    @Builder.Default
    private Boolean verified = false;

    @Column(name = "verification_token", length = 500)
    private String verificationToken;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "coin_transaction_id",
            referencedColumnName = "id",
            nullable = true,
            foreignKey = @ForeignKey(name = "fk_ad_views_coin_transaction")
    )
    private CoinTransaction coinTransaction;

    @Column(name = "view_date", nullable = false)
    @Builder.Default
    private LocalDate viewDate = LocalDate.now();

    @CreationTimestamp
    @Column(name = "viewed_at", nullable = false, updatable = false)
    private Instant viewedAt;

    public void markVerified(CoinTransaction transaction, int coinsGiven) {
        this.verified         = true;
        this.verifiedAt       = Instant.now();
        this.coinsRewarded    = coinsGiven;
        this.coinTransaction  = transaction;
    }
}