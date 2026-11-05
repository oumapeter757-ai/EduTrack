package com.peterscode.edutrack.enrollment.event;
public record StudentExitedEvent(Long schoolId, Long studentId, Long enrollmentId, String outcome, Long runId) {}
