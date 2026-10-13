package com.peterscode.edutrack.school.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Service modules available across the EduTrack enterprise platform.
 * Schools have granular entitlements enabling specific services.
 */
public enum ServiceModule {
    STUDENT_MANAGEMENT,
    TEACHER_MANAGEMENT,
    ATTENDANCE,
    GRADING,
    EXAMINATION,
    TIMETABLE,
    BILLING,
    LIBRARY,
    TRANSPORT,
    LMS,
    NOTIFICATIONS,
    REPORTS;

    /**
     * Default service modules provided for each tier.
     */
    public static Set<ServiceModule> getDefaultModulesForTier(SubscriptionTier tier) {
        if (tier == null) {
            return EnumSet.of(STUDENT_MANAGEMENT, TEACHER_MANAGEMENT, ATTENDANCE);
        }
        return switch (tier) {
            case FREE -> EnumSet.of(STUDENT_MANAGEMENT, TEACHER_MANAGEMENT, ATTENDANCE);
            case BASIC -> EnumSet.of(STUDENT_MANAGEMENT, TEACHER_MANAGEMENT, ATTENDANCE, GRADING, TIMETABLE);
            case STANDARD -> EnumSet.of(
                    STUDENT_MANAGEMENT, TEACHER_MANAGEMENT, ATTENDANCE, GRADING,
                    EXAMINATION, TIMETABLE, NOTIFICATIONS, REPORTS
            );
            case PREMIUM -> EnumSet.of(
                    STUDENT_MANAGEMENT, TEACHER_MANAGEMENT, ATTENDANCE, GRADING,
                    EXAMINATION, TIMETABLE, BILLING, LIBRARY, NOTIFICATIONS, REPORTS
            );
            case ENTERPRISE -> EnumSet.allOf(ServiceModule.class);
        };
    }
}
