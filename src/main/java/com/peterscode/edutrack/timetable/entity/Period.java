package com.peterscode.edutrack.timetable.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalTime;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "periods",
        uniqueConstraints = @UniqueConstraint(name = "uk_period_school_sort", columnNames = {"school_id", "sort_order"}),
        indexes = @Index(name = "idx_period_school", columnList = "school_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Period extends BaseEntity {
    @Column(name = "school_id", nullable = false)
    private Long schoolId;
    @Column(nullable = false, length = 50)
    private String name;
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PeriodType type;
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    public enum PeriodType { LESSON, BREAK }
}
