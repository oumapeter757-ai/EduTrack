package com.peterscode.edutrack.student.exception;

public class StudentCapacityExceededException extends IllegalStateException {

    public StudentCapacityExceededException(String message) {
        super(message);
    }

    public StudentCapacityExceededException(Long schoolId, int maxStudents) {
        super(String.format("School with ID %d has reached its maximum student capacity of %d", schoolId, maxStudents));
    }
}
