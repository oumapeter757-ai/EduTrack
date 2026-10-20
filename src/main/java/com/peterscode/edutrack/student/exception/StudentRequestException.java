package com.peterscode.edutrack.student.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class StudentRequestException extends RuntimeException {
    public StudentRequestException(String message) {
        super(message);
    }
}
