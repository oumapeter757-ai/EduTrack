package com.peterscode.edutrack.student.exception;

import com.peterscode.edutrack.shared.exception.ResourceNotFoundException;

public class StudentNotFoundException extends ResourceNotFoundException {

    public StudentNotFoundException(String message) {
        super(message);
    }

    public StudentNotFoundException(Long id) {
        super("Student not found");
    }

    public static StudentNotFoundException byAdmissionNumber(String admissionNumber) {
        return new StudentNotFoundException("Student not found");
    }

    public static StudentNotFoundException byUserId(Long userId) {
        return new StudentNotFoundException("Student profile not found");
    }
}
