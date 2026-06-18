package com.chandan.randomchat.model;

import com.chandan.randomchat.model.enums.PurchaseStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "purchases",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_purchases_token", columnNames = "purchase_token")
        },
        indexes = {
                @Index(name = "idx_purchases_token",     columnList = "purchase_token"),
                @Index(name = "idx_purchases_user_date", columnList = "user_id, purchased_at DESC"),
                @Index(name = "idx_purchases_app_date",  columnList = "app_id, purchased_at DESC"),
                @Index(name = "idx_purchases_pending",   columnList = "app_id, created_at ASC"),
                @Index(name = "idx_purchases_product",   columnList = "app_id, product_id, purchased_at DESC")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"user", "coinTransaction"})
public class Purchase {

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
            foreignKey = @ForeignKey(name = "fk_purchases_user")
    )
    private User user;

    @Column(name = "product_id", nullable = false, length = 100)
    private String productId;

    @Column(name = "purchase_token", nullable = false, length = 500, unique = true)
    private String purchaseToken;

    @Column(name = "order_id", length = 200)
    private String orderId;

    @Column(name = "package_name", length = 200)
    private String packageName;

    @Column(name = "coins_granted", nullable = false)
    @Builder.Default
    private Integer coinsGranted = 0;

    @Column(name = "amount_usd", precision = 10, scale = 2)
    private BigDecimal amountUsd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PurchaseStatus status = PurchaseStatus.PENDING;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "coin_transaction_id",
            referencedColumnName = "id",
            nullable = true,
            foreignKey = @ForeignKey(name = "fk_purchases_coin_transaction")
    )
    private CoinTransaction coinTransaction;

    @Column(name = "purchased_at", nullable = false)
    @Builder.Default
    private Instant purchasedAt = Instant.now();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public void markVerified(String orderId, BigDecimal amountUsd, CoinTransaction transaction) {
        this.status          = PurchaseStatus.VERIFIED;
        this.orderId         = orderId;
        this.amountUsd       = amountUsd;
        this.verifiedAt      = Instant.now();
        this.coinTransaction = transaction;
    }

    public void markFailed(String reason) {
        this.status        = PurchaseStatus.FAILED;
        this.failureReason = reason;
        this.verifiedAt    = Instant.now();
    }

    public void markDuplicate() {
        this.status = PurchaseStatus.DUPLICATE;
    }

    public boolean isPending()  { return status == PurchaseStatus.PENDING; }
    public boolean isVerified() { return status == PurchaseStatus.VERIFIED; }
}