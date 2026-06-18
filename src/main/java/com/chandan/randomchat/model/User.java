package com.chandan.randomchat.model;

import com.chandan.randomchat.model.enums.GenderType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_users_device_app",    columnNames = {"device_id_hash", "app_id"}),
                @UniqueConstraint(name = "uq_users_username_app",  columnNames = {"username", "app_id"})
        },
        indexes = {
                @Index(name = "idx_users_app_id",       columnList = "app_id, created_at DESC"),
                @Index(name = "idx_users_country",       columnList = "app_id, country_code"),
                @Index(name = "idx_users_banned",        columnList = "app_id, is_banned, ban_expires_at"),
                @Index(name = "idx_users_match_credits", columnList = "app_id, match_credits")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"onlineUser", "sessionsAsUser1", "sessionsAsUser2",
        "reportsAsFiled", "reportsAsSubject", "bans",
        "coinTransactions", "adViews", "purchases"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "app_id", nullable = false, length = 50)
    private String appId;

    @Column(name = "device_id_hash", nullable = false, length = 64)
    private String deviceIdHash;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private GenderType gender;

    @Column(name = "country_code", length = 5)
    private String countryCode;

    @Column(name = "coin_balance", nullable = false)
    @Builder.Default
    private Integer coinBalance = 0;

    @Column(name = "match_credits", nullable = false)
    @Builder.Default
    private Integer matchCredits = 0;

    @Column(name = "filter_credits_gender", nullable = false)
    @Builder.Default
    private Integer filterCreditsGender = 0;

    @Column(name = "filter_credits_country", nullable = false)
    @Builder.Default
    private Integer filterCreditsCountry = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "active_gender_filter", length = 10)
    private GenderType activeGenderFilter;

    @Column(name = "active_country_filter", length = 5)
    private String activeCountryFilter;

    @Column(name = "is_banned", nullable = false)
    @Builder.Default
    private Boolean isBanned = false;

    @Column(name = "ban_reason", columnDefinition = "TEXT")
    private String banReason;

    @Column(name = "ban_expires_at")
    private Instant banExpiresAt;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private OnlineUser onlineUser;

    @OneToMany(mappedBy = "user1", fetch = FetchType.LAZY)
    private List<Session> sessionsAsUser1;

    @OneToMany(mappedBy = "user2", fetch = FetchType.LAZY)
    private List<Session> sessionsAsUser2;

    @OneToMany(mappedBy = "reporter", fetch = FetchType.LAZY)
    private List<Report> reportsAsFiled;

    @OneToMany(mappedBy = "reported", fetch = FetchType.LAZY)
    private List<Report> reportsAsSubject;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Ban> bans;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<CoinTransaction> coinTransactions;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<AdView> adViews;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Purchase> purchases;

    public boolean canMatch() {
        return !isBanned
                && !isDeleted
                && matchCredits > 0
                && (banExpiresAt == null || Instant.now().isBefore(banExpiresAt));
    }

    public boolean hasActiveGenderFilter() {
        return activeGenderFilter != null && filterCreditsGender > 0;
    }

    public boolean hasActiveCountryFilter() {
        return activeCountryFilter != null && filterCreditsCountry > 0;
    }

    public boolean clearExpiredFilters() {
        boolean changed = false;
        if (filterCreditsGender <= 0 && activeGenderFilter != null) {
            activeGenderFilter = null;
            changed = true;
        }
        if (filterCreditsCountry <= 0 && activeCountryFilter != null) {
            activeCountryFilter = null;
            changed = true;
        }
        return changed;
    }

    public boolean isBanExpired() {
        return isBanned && banExpiresAt != null && Instant.now().isAfter(banExpiresAt);
    }
}