package com.peterscode.edutrack.classlevel.exception;

import com.peterscode.edutrack.shared.exception.ResourceNotFoundException;

public class StreamNotFoundException extends ResourceNotFoundException {

    public StreamNotFoundException(String message) {
        super(message);
    }

    public StreamNotFoundException(Long id) {
        super("Stream", "id", id);
    }

    public static StreamNotFoundException byCode(String code) {
        return new StreamNotFoundException(String.format("Stream not found with code: '%s'", code));
    }
}
