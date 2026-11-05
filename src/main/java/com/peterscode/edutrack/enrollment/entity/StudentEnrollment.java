package com.peterscode.edutrack.enrollment.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@Entity
@Table(name = "student_enrollments", indexes = {
        @Index(name = "idx_enrollment_school_student", columnList = "school_id, student_id"),
        @Index(name = "idx_enrollment_school_year", columnList = "school_id, academic_year_id"),
        @Index(name = "idx_enrollment_school_class", columnList = "school_id, class_level_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class StudentEnrollment extends BaseEntity {
    @Column(name = "school_id", nullable = false) private Long schoolId;
    @Column(name = "student_id", nullable = false) private Long studentId;
    @Column(name = "academic_year_id") private Long academicYearId;
    @Column(name = "class_level_id") private Long classLevelId;
    @Column(name = "stream_id") private Long streamId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private EnrollmentStatus status;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate;
    @Column(name = "outcome_reason", length = 500) private String outcomeReason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private EnrollmentSource source;
    @Column(name = "promotion_run_id") private Long promotionRunId;
    @Column(name = "current_marker") private Integer currentMarker;
    @Column(name = "non_voided_marker", insertable = false, updatable = false) private Integer nonVoidedMarker;
    @Version @Column(nullable = false) private Long version;
}
