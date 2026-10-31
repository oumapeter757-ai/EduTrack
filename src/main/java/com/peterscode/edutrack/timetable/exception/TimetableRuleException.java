package com.peterscode.edutrack.timetable.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class TimetableRuleException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus status;

    public TimetableRuleException(String errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

}
