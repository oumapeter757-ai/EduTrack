package com.peterscode.edutrack.enrollment.dto;

import com.peterscode.edutrack.enrollment.entity.*;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class EnrollmentView {
    Long id;
    Long schoolId;
    Long studentId;
    Long academicYearId;
    Long classLevelId;
    Long streamId;
    EnrollmentStatus status;
    LocalDate startDate;
    LocalDate endDate;
    String outcomeReason;
    EnrollmentSource source;
}
