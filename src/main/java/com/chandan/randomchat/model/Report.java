package com.chandan.randomchat.model;

import com.chandan.randomchat.model.enums.ReportReason;
import com.chandan.randomchat.model.enums.ReportStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "reports",
        indexes = {
                @Index(name = "idx_reports_pending",        columnList = "app_id, status, created_at ASC"),
                @Index(name = "idx_reports_reported_user",  columnList = "reported_id, created_at DESC"),
                @Index(name = "idx_reports_reporter",       columnList = "reporter_id, created_at DESC"),
                @Index(name = "idx_reports_session",        columnList = "session_id"),
                @Index(name = "idx_reports_auto_ban_check", columnList = "reported_id, app_id, status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"reporter", "reported", "session"})
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "app_id", nullable = false, length = 50)
    private String appId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "reporter_id",
            referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_reports_reporter"),
            nullable = true
    )
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "reported_id",
            referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_reports_reported"),
            nullable = true
    )
    private User reported;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "session_id",
            referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_reports_session"),
            nullable = true
    )
    private Session session;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "reason", nullable = false, length = 50, columnDefinition = "reason")
    private ReportReason reason;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "status")
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public void markReviewed(String adminUsername, ReportStatus newStatus, String notes) {
        this.reviewedBy  = adminUsername;
        this.reviewedAt  = Instant.now();
        this.status      = newStatus;
        this.adminNotes  = notes;
    }

    public boolean isPending() {
        return status == ReportStatus.PENDING;
    }
}