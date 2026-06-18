package com.chandan.randomchat.exception;

import org.springframework.http.HttpStatus;

public class AppNotFoundException extends AppException {

    public AppNotFoundException(String appId) {
        super(
                "APP_NOT_FOUND",
                "App not registered: " + appId,
                HttpStatus.BAD_REQUEST
        );
    }
}