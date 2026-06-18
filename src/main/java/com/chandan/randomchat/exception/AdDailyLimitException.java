package com.chandan.randomchat.exception;

import org.springframework.http.HttpStatus;

public class AdDailyLimitException extends AppException {

    public AdDailyLimitException(int cap) {
        super(
                "AD_DAILY_LIMIT_REACHED",
                "Daily ad limit of " + cap +
                        " reached. Come back tomorrow.",
                HttpStatus.BAD_REQUEST
        );
    }
}