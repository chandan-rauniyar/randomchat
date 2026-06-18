package com.chandan.randomchat.exception;

import org.springframework.http.HttpStatus;

public class DuplicatePurchaseException extends AppException {

    public DuplicatePurchaseException(String token) {
        super(
                "DUPLICATE_PURCHASE",
                "Purchase already processed: " + token,
                HttpStatus.CONFLICT
        );
    }
}