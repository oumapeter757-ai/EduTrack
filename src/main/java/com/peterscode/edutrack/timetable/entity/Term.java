package com.peterscode.edutrack.timetable.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDate;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "terms",
        uniqueConstraints = @UniqueConstraint(name = "uk_term_school_year_name",
                columnNames = {"school_id", "academic_year_id", "name"}),
        indexes = {@Index(name = "idx_term_school", columnList = "school_id"),
                @Index(name = "idx_term_academic_year", columnList = "academic_year_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Term extends BaseEntity {
    @Column(name = "school_id", nullable = false)
    private Long schoolId;
    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;
    @Column(nullable = false, length = 50)
    private String name;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;
}
