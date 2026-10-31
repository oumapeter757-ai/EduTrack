package com.peterscode.edutrack.timetable.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "timetable_lessons",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_lesson_stream_slot", columnNames = {"timetable_id", "day_of_week", "period_id", "stream_id"}),
                @UniqueConstraint(name = "uk_lesson_teacher_slot", columnNames = {"timetable_id", "day_of_week", "period_id", "teacher_id"}),
                @UniqueConstraint(name = "uk_lesson_room_slot", columnNames = {"timetable_id", "day_of_week", "period_id", "room_id"})
        },
        indexes = {@Index(name = "idx_lesson_school", columnList = "school_id"), @Index(name = "idx_lesson_timetable", columnList = "timetable_id"),
                @Index(name = "idx_lesson_stream_subject", columnList = "stream_subject_id"), @Index(name = "idx_lesson_teacher", columnList = "teacher_id"),
                @Index(name = "idx_lesson_room", columnList = "room_id"), @Index(name = "idx_lesson_period", columnList = "period_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TimetableLesson extends BaseEntity {
    @Column(name = "school_id", nullable = false)
    private Long schoolId;
    @Column(name = "timetable_id", nullable = false)
    private Long timetableId;
    @Column(name = "stream_subject_id", nullable = false)
    private Long streamSubjectId;
    @Column(name = "stream_id", nullable = false)
    private Long streamId;
    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;
    @Column(name = "room_id")
    private Long roomId;
    @Column(name = "period_id", nullable = false)
    private Long periodId;
    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;
}
