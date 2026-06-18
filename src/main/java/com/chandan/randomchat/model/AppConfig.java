package com.chandan.randomchat.model;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_config",
        uniqueConstraints = {
            @UniqueConstraint(name = "uq_app_config_key", columnNames = {"app_id", "config_key"})
        },
        indexes = {
        @Index(name = "idx_app_config_app_id", columnList = "app_id")
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class AppConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false,  updatable = false)
    private UUID id;

    @Column(name = "app_id", nullable = false, length = 50)
    private String appId;

    @Column(name = "config_key", nullable = false, length = 100)
    private String configKey;

    @Column(name = "config_value", nullable = false, length = 255)
    private String configValue;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    @Builder.Default
    private String updatedBy = "SYSTEM";

    public Integer getValueAsInt() {
        return Integer.parseInt(this.configValue);
    }

    public Boolean getValueAsBoolean() {
        return Boolean.parseBoolean(this.configValue);
    }
}
