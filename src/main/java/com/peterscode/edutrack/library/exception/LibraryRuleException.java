package com.peterscode.edutrack.library.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class LibraryRuleException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus status;

    public LibraryRuleException(String errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }
}
