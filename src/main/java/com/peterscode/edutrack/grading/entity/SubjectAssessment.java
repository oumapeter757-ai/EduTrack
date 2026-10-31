package com.peterscode.edutrack.grading.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "subject_assessments")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class SubjectAssessment extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "term_id", nullable = false)
    private Long termId;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "assessment_type_id", nullable = false)
    private Long assessmentTypeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_type_id", insertable = false, updatable = false)
    private AssessmentType assessmentType;

    @Column(nullable = false)
    private Boolean active;
}
