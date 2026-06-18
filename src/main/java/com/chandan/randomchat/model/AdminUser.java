package com.chandan.randomchat.model;

import com.chandan.randomchat.model.enums.AdminRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "admin_users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_admin_username", columnNames = "username"),
                @UniqueConstraint(name = "uq_admin_email",    columnNames = "email")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "activityLogs")
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "username", nullable = false, length = 50, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "email", length = 200, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private AdminRole role = AdminRole.MODERATOR;

    @Column(name = "allowed_app_ids", columnDefinition = "TEXT[]")
    private String[] allowedAppIds;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @OneToMany(mappedBy = "admin", fetch = FetchType.LAZY)
    private List<AdminActivityLog> activityLogs;

    public boolean canAccessApp(String appId) {
        if (allowedAppIds == null) return true;
        for (String id : allowedAppIds) {
            if (id.equals(appId)) return true;
        }
        return false;
    }

    public boolean isSuperAdmin()  { return role == AdminRole.SUPER_ADMIN; }
    public boolean isModerator()   { return role == AdminRole.MODERATOR || role == AdminRole.SUPER_ADMIN; }
    public boolean isAnalyst()     { return true; }
}