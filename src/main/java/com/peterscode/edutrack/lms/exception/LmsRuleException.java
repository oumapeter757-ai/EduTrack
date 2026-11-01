package com.peterscode.edutrack.lms.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class LmsRuleException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus status;

    public LmsRuleException(String errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }
}
