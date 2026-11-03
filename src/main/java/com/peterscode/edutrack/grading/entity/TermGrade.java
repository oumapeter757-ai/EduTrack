package com.peterscode.edutrack.grading.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "term_grades")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class TermGrade extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "term_id", nullable = false)
    private Long termId;

    @Column(name = "stream_id", nullable = false)
    private Long streamId;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "total_raw_score", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalRawScore;

    @Column(name = "total_max_score", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalMaxScore;

    @Column(name = "weighted_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightedPercentage;

    @Column(name = "letter_grade", nullable = false, length = 10)
    private String letterGrade;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal gpa;

    @Column(name = "stream_rank", nullable = false)
    private Integer streamRank;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

    @Column(name = "computed_by", nullable = false)
    private Long computedBy;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;
}
