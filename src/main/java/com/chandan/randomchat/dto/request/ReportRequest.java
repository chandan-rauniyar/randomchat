package com.chandan.randomchat.dto.request;

import com.chandan.randomchat.model.enums.ReportReason;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportRequest {

    @NotNull(message = "reportedUserId is required")
    private UUID reportedUserId;

    /** Optional: which session did this happen in. */
    private UUID sessionId;

    @NotNull(message = "reason is required")
    private ReportReason reason;

    /** Optional free text from the user. Max 500 chars. */
    @lombok.Builder.Default
    private String description = null;
}
