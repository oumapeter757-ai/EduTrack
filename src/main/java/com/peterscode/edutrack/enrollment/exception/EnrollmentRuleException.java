package com.peterscode.edutrack.enrollment.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class EnrollmentRuleException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus status;

    public EnrollmentRuleException(String message, String errorCode, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }
}
