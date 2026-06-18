package com.chandan.randomchat.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class AppException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    protected AppException(String errorCode,
                           String message,
                           HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }
}