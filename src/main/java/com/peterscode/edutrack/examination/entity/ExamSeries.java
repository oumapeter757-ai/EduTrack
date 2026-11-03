package com.peterscode.edutrack.examination.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "exam_series",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_exam_series_school_term_name",
        columnNames = {"school_id", "term_id", "name"}),
    indexes = {
        @Index(name = "idx_exam_series_school", columnList = "school_id"),
        @Index(name = "idx_exam_series_term", columnList = "term_id"),
        @Index(name = "idx_exam_series_school_status", columnList = "school_id, status")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ExamSeries extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "term_id", nullable = false)
    private Long termId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "registration_opens_on", nullable = false)
    private LocalDate registrationOpensOn;

    @Column(name = "registration_closes_on", nullable = false)
    private LocalDate registrationClosesOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private SeriesStatus status = SeriesStatus.DRAFT;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @OneToMany(mappedBy = "examSeries", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ExamRegistration> registrations = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    public enum SeriesStatus {
        DRAFT, REGISTRATION_OPEN, REGISTRATION_CLOSED, COMPLETED
    }
}
