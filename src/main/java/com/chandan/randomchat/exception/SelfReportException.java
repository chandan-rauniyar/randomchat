package com.chandan.randomchat.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * SelfReportException — thrown when a user tries to report themselves.
 * Returns 400 BAD_REQUEST with error code SELF_REPORT_NOT_ALLOWED.
 *
 * Replaces the raw IllegalArgumentException that was returning 500.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class SelfReportException extends AppException {
    public SelfReportException() {
        super("SELF_REPORT_NOT_ALLOWED", "You cannot report yourself", HttpStatus.BAD_REQUEST);
    }
}