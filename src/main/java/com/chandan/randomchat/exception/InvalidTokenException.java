package com.chandan.randomchat.exception;

import org.springframework.http.HttpStatus;

public class InvalidTokenException extends AppException {

    public InvalidTokenException(String reason) {
        super(
                "INVALID_TOKEN",
                "JWT invalid: " + reason,
                HttpStatus.UNAUTHORIZED
        );
    }
}