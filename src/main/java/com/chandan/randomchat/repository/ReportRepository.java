package com.chandan.randomchat.repository;

import com.chandan.randomchat.model.Report;
import com.chandan.randomchat.model.enums.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    /**
     * Auto-ban check: how many active reports against this user?
     * Called by BanService after every new report submission.
     * Uses idx_reports_auto_ban_check.
     */
    int countByReportedIdAndAppIdAndStatusIn(
            UUID reportedId, String appId, List<ReportStatus> statuses);

    /** Admin moderation queue — pending reports, oldest first. */
    Page<Report> findByAppIdAndStatusOrderByCreatedAtAsc(
            String appId, ReportStatus status, Pageable pageable);

    /** All reports against a specific user — for admin user detail page. */
    List<Report> findByReportedIdOrderByCreatedAtDesc(UUID reportedId);
}
