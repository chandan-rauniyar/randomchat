package com.chandan.randomchat.exception;

import org.springframework.http.HttpStatus;

public class InsufficientCreditsException extends AppException {

    public InsufficientCreditsException(String creditType) {
        super(
                "INSUFFICIENT_" + creditType + "_CREDITS",
                "No " + creditType.toLowerCase() + " credits remaining",
                HttpStatus.BAD_REQUEST
        );
    }
}