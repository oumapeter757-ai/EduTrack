package com.peterscode.edutrack.shared.exception;

public class SchoolRequiredException extends RuntimeException {
    public SchoolRequiredException() {
        super("School context is required");
    }
}
