package com.chandan.randomchat.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
        name = "admin_activity_log",
        indexes = {
                @Index(name = "idx_admin_log_admin_date", columnList = "admin_id, created_at DESC"),
                @Index(name = "idx_admin_log_target",     columnList = "target_type, target_id, created_at DESC"),
                @Index(name = "idx_admin_log_app_date",   columnList = "app_id, created_at DESC")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "admin")
public class AdminActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "admin_id",
            referencedColumnName = "id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_admin_log_admin")
    )
    private AdminUser admin;

    @Column(name = "admin_username", nullable = false, length = 50)
    private String adminUsername;

    @Column(name = "app_id", length = 50)
    private String appId;

    @Column(name = "action_type", nullable = false, length = 100)
    private String actionType;

    @Column(name = "target_type", length = 50)
    private String targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "ip_address", columnDefinition = "INET")
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AdminActivityLog of(AdminUser admin, String appId,
                                      String actionType, String targetType,
                                      UUID targetId, Map<String, Object> details) {
        return AdminActivityLog.builder()
                .admin(admin)
                .adminUsername(admin.getUsername())
                .appId(appId)
                .actionType(actionType)
                .targetType(targetType)
                .targetId(targetId)
                .details(details)
                .build();
    }
}