package com.peterscode.edutrack.academicyear.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@Entity
@Table(
        name = "academic_years",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_academic_year_school_name", columnNames = {"school_id", "name"})
        },
        indexes = {
                @Index(name = "idx_academic_year_school", columnList = "school_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AcademicYear extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "current", nullable = false)
    @Builder.Default
    private boolean current = false;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
