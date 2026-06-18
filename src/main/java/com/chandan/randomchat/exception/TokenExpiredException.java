package com.chandan.randomchat.exception;

import org.springframework.http.HttpStatus;

public class TokenExpiredException extends AppException {

    public TokenExpiredException() {
        super(
                "TOKEN_EXPIRED",
                "JWT has expired. Call /api/user/init to get a new token.",
                HttpStatus.UNAUTHORIZED
        );
    }
}