package com.peterscode.edutrack.enrollment.event;
public record StudentPromotedEvent(Long schoolId, Long studentId, Long fromEnrollmentId, Long toEnrollmentId, Long runId) {}
