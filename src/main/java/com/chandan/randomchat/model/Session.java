package com.chandan.randomchat.model;

import com.chandan.randomchat.model.enums.GenderType;
import com.chandan.randomchat.model.enums.SessionEndReason;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "sessions",
        indexes = {
                @Index(name = "idx_sessions_user1_date",      columnList = "user1_id, started_at DESC"),
                @Index(name = "idx_sessions_user2_date",      columnList = "user2_id, started_at DESC"),
                @Index(name = "idx_sessions_app_date",        columnList = "app_id, started_at DESC"),
                @Index(name = "idx_sessions_reported",        columnList = "app_id, was_reported, started_at DESC"),
                @Index(name = "idx_sessions_gender_filter",   columnList = "app_id, user1_gender_filter, user2_gender_filter"),
                @Index(name = "idx_sessions_country_filter",  columnList = "app_id, user1_country_filter, user2_country_filter")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"user1", "user2", "reports"})
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "app_id", nullable = false, length = 50)
    private String appId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user1_id",
            referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_sessions_user1"),
            nullable = true
    )
    private User user1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user2_id",
            referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_sessions_user2"),
            nullable = true
    )
    private User user2;

    @Enumerated(EnumType.STRING)
    @Column(name = "user1_gender_filter", length = 10)
    private GenderType user1GenderFilter;

    @Column(name = "user1_country_filter", length = 5)
    private String user1CountryFilter;

    @Enumerated(EnumType.STRING)
    @Column(name = "user2_gender_filter", length = 10)
    private GenderType user2GenderFilter;

    @Column(name = "user2_country_filter", length = 5)
    private String user2CountryFilter;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 30)
    private SessionEndReason endReason;

    @Column(name = "was_reported", nullable = false)
    @Builder.Default
    private Boolean wasReported = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "session", fetch = FetchType.LAZY)
    private List<Report> reports;

    public void endSession(SessionEndReason reason) {
        this.endedAt  = Instant.now();
        this.endReason = reason;
        if (this.startedAt != null) {
            this.durationSec = (int) java.time.Duration.between(startedAt, endedAt).getSeconds();
        }
    }

    public boolean involvesUser(UUID userId) {
        return (user1 != null && user1.getId().equals(userId))
                || (user2 != null && user2.getId().equals(userId));
    }

    public User getPartner(UUID myUserId) {
        if (user1 != null && user1.getId().equals(myUserId)) return user2;
        if (user2 != null && user2.getId().equals(myUserId)) return user1;
        return null;
    }
}