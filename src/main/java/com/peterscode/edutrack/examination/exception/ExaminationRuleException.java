package com.peterscode.edutrack.examination.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ExaminationRuleException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus httpStatus;

    public ExaminationRuleException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
