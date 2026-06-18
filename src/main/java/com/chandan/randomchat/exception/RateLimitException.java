package com.chandan.randomchat.exception;

import org.springframework.http.HttpStatus;

public class RateLimitException extends AppException {

    public RateLimitException(String action) {
        super(
                "RATE_LIMIT_EXCEEDED",
                "Too many requests for: " + action,
                HttpStatus.TOO_MANY_REQUESTS
        );
    }
}