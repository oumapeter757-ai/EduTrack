package com.peterscode.edutrack.subject.exception;

import com.peterscode.edutrack.shared.exception.ResourceNotFoundException;

public class SubjectNotFoundException extends ResourceNotFoundException {

    public SubjectNotFoundException(String message) {
        super(message);
    }

    public SubjectNotFoundException(Long id) {
        super("Subject", "id", id);
    }

    public static SubjectNotFoundException byCode(Long schoolId, String code) {
        return new SubjectNotFoundException(String.format("Subject not found with code '%s' for school id %d", code, schoolId));
    }
}
