package com.peterscode.edutrack.examination.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "exam_registrations",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_exam_reg_school_series_student_paper",
            columnNames = {"school_id", "exam_series_id", "student_id", "assessment_paper_id"}),
        @UniqueConstraint(
            name = "uk_exam_reg_school_series_candidate",
            columnNames = {"school_id", "exam_series_id", "candidate_number"})
    },
    indexes = {
        @Index(name = "idx_exam_reg_school", columnList = "school_id"),
        @Index(name = "idx_exam_reg_series", columnList = "exam_series_id"),
        @Index(name = "idx_exam_reg_student", columnList = "student_id"),
        @Index(name = "idx_exam_reg_school_series", columnList = "school_id, exam_series_id"),
        @Index(name = "idx_exam_reg_candidate", columnList = "school_id, exam_series_id, candidate_number")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ExamRegistration extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "exam_series_id", nullable = false)
    private Long examSeriesId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_series_id", insertable = false, updatable = false)
    private ExamSeries examSeries;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "assessment_paper_id", nullable = false)
    private Long assessmentPaperId;

    @Column(name = "candidate_number", nullable = false, length = 20)
    private String candidateNumber;

    @Column(name = "class_level_id", nullable = false)
    private Long classLevelId;

    @Column(name = "stream_id", nullable = false)
    private Long streamId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RegistrationStatus status = RegistrationStatus.REGISTERED;

    @Column(length = 500)
    private String reason;

    @Column(name = "registered_by", nullable = false)
    private Long registeredBy;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    public enum RegistrationStatus {
        REGISTERED, WITHDRAWN, DEBARRED
    }
}
