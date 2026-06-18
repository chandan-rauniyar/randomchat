package com.chandan.randomchat.exception;

import org.springframework.http.HttpStatus;

public class PriceMismatchException extends AppException {

    public PriceMismatchException(int expected, int received) {
        super(
                "PRICE_MISMATCH",
                "Server price is " + expected +
                        " coins, client sent " + received,
                HttpStatus.BAD_REQUEST
        );
    }
}