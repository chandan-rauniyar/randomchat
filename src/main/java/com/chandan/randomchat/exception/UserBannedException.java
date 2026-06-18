package com.chandan.randomchat.exception;

import org.springframework.http.HttpStatus;

import java.time.Instant;

public class UserBannedException extends AppException {

    public UserBannedException(String reason, Instant expiresAt) {
        super(
                "USER_BANNED",
                "Account banned: " + reason +
                        (expiresAt != null ? " until " + expiresAt : " permanently"),
                HttpStatus.FORBIDDEN
        );
    }
}