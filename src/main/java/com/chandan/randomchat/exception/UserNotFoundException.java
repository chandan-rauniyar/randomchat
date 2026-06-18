package com.chandan.randomchat.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class UserNotFoundException extends AppException {

    public UserNotFoundException(UUID userId) {
        super(
                "USER_NOT_FOUND",
                "User not found: " + userId,
                HttpStatus.NOT_FOUND
        );
    }
}