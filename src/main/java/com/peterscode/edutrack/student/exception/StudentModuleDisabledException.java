package com.peterscode.edutrack.student.exception;

import com.peterscode.edutrack.shared.exception.UnauthorizedOperationException;

public class StudentModuleDisabledException extends UnauthorizedOperationException {

    public StudentModuleDisabledException(String message) {
        super(message);
    }

    public StudentModuleDisabledException(Long schoolId) {
        super(String.format("Student management module is not enabled for school with ID %d", schoolId));
    }
}
