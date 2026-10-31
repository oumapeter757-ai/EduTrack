package com.peterscode.edutrack.grading.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class GradingEx extends RuntimeException {
    private final String errorCode;
    private final HttpStatus status;

    public GradingEx(String errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }
}
