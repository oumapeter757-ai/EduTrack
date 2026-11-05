package com.peterscode.edutrack.enrollment.event;
public record StudentGraduatedEvent(Long schoolId, Long studentId, Long enrollmentId, Long runId) {}
