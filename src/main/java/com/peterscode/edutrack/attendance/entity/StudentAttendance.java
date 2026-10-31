package com.peterscode.edutrack.attendance.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import java.time.LocalDate;

@Entity
@Table(name = "student_attendance",
        uniqueConstraints = @UniqueConstraint(name = "uk_attendance_school_student_date_scope",
                columnNames = {"school_id", "student_id", "attendance_date", "lesson_scope_id"}),
        indexes = {
        @Index(name = "idx_attendance_school_stream_date", columnList = "school_id, stream_id, attendance_date"),
        @Index(name = "idx_attendance_school_student_date", columnList = "school_id, student_id, attendance_date"),
        @Index(name = "idx_attendance_school_term_date", columnList = "school_id, term_id, attendance_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class StudentAttendance extends BaseEntity {
    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "term_id", nullable = false)
    private Long termId;

    @Column(name = "stream_id", nullable = false)
    private Long streamId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_type", nullable = false, length = 16)
    private AttendanceType attendanceType;

    @Column(name = "timetable_lesson_id")
    private Long timetableLessonId;

    @Column(name = "lesson_scope_id", insertable = false, updatable = false)
    private Long lessonScopeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AttendanceStatus status;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "marked_by", nullable = false)
    private Long markedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
