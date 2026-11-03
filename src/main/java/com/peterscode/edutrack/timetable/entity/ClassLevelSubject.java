package com.peterscode.edutrack.timetable.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "class_level_subjects",
        uniqueConstraints = @UniqueConstraint(name = "uk_class_level_subject", columnNames = {"class_level_id", "subject_id"}),
        indexes = {@Index(name = "idx_cls_school", columnList = "school_id"), @Index(name = "idx_cls_subject", columnList = "subject_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ClassLevelSubject extends BaseEntity {
    @Column(name = "school_id", nullable = false)
    private Long schoolId;
    @Column(name = "class_level_id", nullable = false)
    private Long classLevelId;
    @Column(name = "subject_id", nullable = false)
    private Long subjectId;
    @Column(name = "default_lessons_per_week", nullable = false)
    private Integer defaultLessonsPerWeek;
    @Builder.Default
    @Column(nullable = false)
    private boolean compulsory = true;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;
}
