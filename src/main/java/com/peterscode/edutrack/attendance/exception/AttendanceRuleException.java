package com.peterscode.edutrack.attendance.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AttendanceRuleException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus status;

    public AttendanceRuleException(String errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }
}
