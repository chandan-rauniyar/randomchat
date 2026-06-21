package com.chandan.randomchat.controller;

import com.chandan.randomchat.dto.request.ReportRequest;
import com.chandan.randomchat.dto.response.ReportResponse;
import com.chandan.randomchat.model.Report;
import com.chandan.randomchat.model.enums.ReportReason;
import com.chandan.randomchat.service.ReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public ResponseEntity<ReportResponse> submitReport(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute("appId") String appId,
            @Valid @RequestBody ReportRequest request) {

        Report saved = reportService.submitReport(
                userId,
                request.getReportedUserId(),
                request.getSessionId(),
                appId,
                request.getReason(),
                request.getDescription()
        );

        return ResponseEntity.ok(new ReportResponse(saved.getId(), "Report submitted"));
    }




}