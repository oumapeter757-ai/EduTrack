package com.peterscode.edutrack.enrollment.service;

public interface EnrollmentUsageContributor {
    boolean hasUsage(Long schoolId, Long studentId, Long enrollmentId);
}
