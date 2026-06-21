package com.chandan.randomchat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class ReportResponse {
    private UUID reportId;
    private String message;
}