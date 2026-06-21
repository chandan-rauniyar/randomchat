package com.chandan.randomchat.service;

import com.chandan.randomchat.model.Report;
import com.chandan.randomchat.model.Session;
import com.chandan.randomchat.model.User;
import com.chandan.randomchat.model.enums.ReportReason;
import com.chandan.randomchat.repository.ReportRepository;
import com.chandan.randomchat.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final ReportRepository  reportRepository;
    private final SessionRepository sessionRepository;
    private final UserService       userService;
    private final BanService        banService;

    @Transactional
    public Report submitReport(UUID reporterUserId,
                               UUID reportedUserId,
                               UUID sessionId,
                               String appId,
                               ReportReason reason,
                               String description) {

        // Load caller — must belong to appId (uses loadUser with app check)
        User reporter = userService.loadUser(reporterUserId, appId);

        // ── FIX 1: use loadAnyUser for the reported user ───────────────────
        // Reported user may be on a DIFFERENT app (cross-app matching).
        // Using loadUser(reportedUserId, appId) would throw INVALID_TOKEN
        // because reported user's appId ≠ reporter's appId.
        // loadAnyUser() skips the app ownership check intentionally.
        // ──────────────────────────────────────────────────────────────────
        User reported = userService.loadAnyUser(reportedUserId);

        // ── FIX 2: throw AppException not IllegalArgumentException ─────────
        // IllegalArgumentException is not handled by GlobalExceptionHandler
        // specifically, so it falls to the generic handler → 500 INTERNAL_ERROR.
        // Throwing our own AppException subclass gives a proper 400 response.
        // ──────────────────────────────────────────────────────────────────
        if (reporterUserId.equals(reportedUserId)) {
            throw new com.chandan.randomchat.exception.SelfReportException();
        }

        Session session = null;
        if (sessionId != null) {
            session = sessionRepository.findById(sessionId).orElse(null);
        }

        Report report = Report.builder()
                .appId(appId)
                .reporter(reporter)
                .reported(reported)
                .session(session)
                .reason(reason)
                .description(description)
                .build();

        Report saved = reportRepository.save(report);

        banService.checkAndAutoBan(reported, saved);

        log.info("Report submitted: reporter={} reported={} reason={}",
                reporterUserId, reportedUserId, reason);

        return saved;
    }
}