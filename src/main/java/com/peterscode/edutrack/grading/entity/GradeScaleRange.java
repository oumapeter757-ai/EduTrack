package com.peterscode.edutrack.grading.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "grade_scale_ranges")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class GradeScaleRange extends BaseEntity {

    @Column(name = "scale_id", nullable = false)
    private Long scaleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scale_id", insertable = false, updatable = false)
    private GradeScale scale;

    @Column(name = "letter_grade", nullable = false, length = 10)
    private String letterGrade;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal gpa;

    @Column(name = "min_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal minPercentage;

    @Column(name = "max_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxPercentage;

    @Column(length = 255)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;
}
