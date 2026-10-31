package com.peterscode.edutrack.timetable.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "stream_subjects",
        uniqueConstraints = @UniqueConstraint(name = "uk_stream_subject_year", columnNames = {"stream_id", "subject_id", "academic_year_id"}),
        indexes = {@Index(name = "idx_ss_school", columnList = "school_id"), @Index(name = "idx_ss_stream", columnList = "stream_id"),
                @Index(name = "idx_ss_subject", columnList = "subject_id"), @Index(name = "idx_ss_teacher", columnList = "teacher_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class StreamSubject extends BaseEntity {
    @Column(name = "school_id", nullable = false)
    private Long schoolId;
    @Column(name = "stream_id", nullable = false)
    private Long streamId;
    @Column(name = "subject_id", nullable = false)
    private Long subjectId;
    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;
    @Column(name = "teacher_id")
    private Long teacherId;
    @Column(name = "lessons_per_week", nullable = false)
    private Integer lessonsPerWeek;
}
