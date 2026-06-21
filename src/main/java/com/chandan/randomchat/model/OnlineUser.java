package com.chandan.randomchat.model;

import com.chandan.randomchat.model.enums.GenderType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "online_users",
        indexes = {
                @Index(name = "idx_online_users_queue",
                        columnList = "app_id, is_in_queue, active_gender_filter, active_country_filter"),
                @Index(name = "idx_online_users_heartbeat", columnList = "last_heartbeat"),
                @Index(name = "idx_online_users_app",       columnList = "app_id"),
                @Index(name = "idx_online_users_global_queue",
                        columnList = "is_in_queue, active_gender_filter, active_country_filter")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "user")
public class OnlineUser {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(
            name = "user_id",
            referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_online_users_user")
    )
    private User user;

    @Column(name = "app_id", nullable = false, length = 50)
    private String appId;

    @Column(name = "last_heartbeat", nullable = false)
    @Builder.Default
    private Instant lastHeartbeat = Instant.now();

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "gender", length = 10, columnDefinition = "gender_type")
    private GenderType gender;

    @Column(name = "country_code", length = 5)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "active_gender_filter", length = 10, columnDefinition = "gender_type")
    private GenderType activeGenderFilter;

    @Column(name = "active_country_filter", length = 5)
    private String activeCountryFilter;

    @Column(name = "is_in_queue", nullable = false)
    @Builder.Default
    private Boolean isInQueue = false;

    @Column(name = "is_in_call", nullable = false)
    @Builder.Default
    private Boolean isInCall = false;

    @Column(name = "current_session_id")
    private UUID currentSessionId;

    @Column(name = "fcm_token", length = 255)
    private String fcmToken;

    public void enterQueue() {
        this.isInQueue = true;
        this.isInCall  = false;
        this.currentSessionId = null;
    }

    public void enterCall(UUID sessionId) {
        this.isInQueue        = false;
        this.isInCall         = true;
        this.currentSessionId = sessionId;
    }

    public void setIdle() {
        this.isInQueue        = false;
        this.isInCall         = false;
        this.currentSessionId = null;
    }
}