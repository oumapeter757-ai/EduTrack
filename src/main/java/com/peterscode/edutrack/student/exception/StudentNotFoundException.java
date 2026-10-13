package com.peterscode.edutrack.student.exception;

import com.peterscode.edutrack.shared.exception.ResourceNotFoundException;

public class StudentNotFoundException extends ResourceNotFoundException {

    public StudentNotFoundException(String message) {
        super(message);
    }

    public StudentNotFoundException(Long id) {
        super("Student", "id", id);
    }

    public static StudentNotFoundException byAdmissionNumber(String admissionNumber) {
        return new StudentNotFoundException(String.format("Student not found with admission number: '%s'", admissionNumber));
    }

    public static StudentNotFoundException byUserId(Long userId) {
        return new StudentNotFoundException(String.format("Student profile not found for user id: '%d'", userId));
    }
}
