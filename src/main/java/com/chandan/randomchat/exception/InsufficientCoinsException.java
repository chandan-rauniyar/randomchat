package com.chandan.randomchat.exception;

import org.springframework.http.HttpStatus;

public class InsufficientCoinsException extends AppException {

    public InsufficientCoinsException(int required, int actual) {
        super(
                "INSUFFICIENT_COINS",
                "Need " + required + " coins but only have " + actual,
                HttpStatus.BAD_REQUEST
        );
    }
}