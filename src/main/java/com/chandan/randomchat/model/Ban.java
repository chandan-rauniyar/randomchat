package com.chandan.randomchat.model;

import com.chandan.randomchat.model.enums.BanSource;
import com.chandan.randomchat.model.enums.BanType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "bans",
        indexes = {
                @Index(name = "idx_bans_user_active", columnList = "user_id, app_id, is_lifted, expires_at"),
                @Index(name = "idx_bans_app_date",    columnList = "app_id, banned_at DESC"),
                @Index(name = "idx_bans_report",      columnList = "report_id"),
                @Index(name = "idx_bans_expired",     columnList = "expires_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "user")
public class Ban {

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
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_bans_user")
    )
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "ban_type", nullable = false, length = 20, columnDefinition = "ban_type")
    private BanType banType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "ban_source", nullable = false, length = 20, columnDefinition = "ban_source")
    @Builder.Default
    private BanSource banSource = BanSource.ADMIN;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "report_id")
    private UUID reportId;

    @Column(name = "banned_by", nullable = false, length = 100)
    @Builder.Default
    private String bannedBy = "SYSTEM";

    @CreationTimestamp
    @Column(name = "banned_at", nullable = false, updatable = false)
    private Instant bannedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "is_lifted", nullable = false)
    @Builder.Default
    private Boolean isLifted = false;

    @Column(name = "lifted_at")
    private Instant liftedAt;

    @Column(name = "lifted_by", length = 100)
    private String liftedBy;

    @Column(name = "lift_reason", columnDefinition = "TEXT")
    private String liftReason;

    public boolean isActive() {
        if (isLifted) return false;
        if (banType == BanType.PERMANENT) return true;
        return expiresAt != null && Instant.now().isBefore(expiresAt);
    }

    public boolean isExpired() {
        return !isLifted
                && banType == BanType.TEMPORARY
                && expiresAt != null
                && Instant.now().isAfter(expiresAt);
    }

    public void lift(String liftedByUsername, String reason) {
        this.isLifted   = true;
        this.liftedAt   = Instant.now();
        this.liftedBy   = liftedByUsername;
        this.liftReason = reason;
    }
}