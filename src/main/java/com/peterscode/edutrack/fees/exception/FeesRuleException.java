package com.peterscode.edutrack.fees.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class FeesRuleException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus status;

    public FeesRuleException(String errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }
}
