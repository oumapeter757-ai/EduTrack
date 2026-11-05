package com.peterscode.edutrack.enrollment.event;
public record StudentRepeatedEvent(Long schoolId, Long studentId, Long fromEnrollmentId, Long toEnrollmentId, Long runId) {}
