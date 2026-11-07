package com.peterscode.edutrack.attendance.repository;

import com.peterscode.edutrack.attendance.entity.AttendanceType;
import com.peterscode.edutrack.attendance.entity.StudentAttendance;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentAttendanceRepository extends JpaRepository<StudentAttendance, Long> {
    List<StudentAttendance> findBySchoolIdAndTermIdAndStreamIdAndAttendanceDateAndAttendanceTypeAndTimetableLessonId(
            Long schoolId, Long termId, Long streamId, LocalDate attendanceDate,
            AttendanceType attendanceType, Long timetableLessonId);

    /**
     * Paginated version for viewing a student's attendance history.
     */
    Page<StudentAttendance> findBySchoolIdAndStudentIdOrderByAttendanceDateDescIdDesc(
            Long schoolId, Long studentId, Pageable pageable);

    /**
     * Unpaginated version — use only when you need the full history (e.g., sync).
     * Limited by the unique constraint on (school, student, date, type, lesson) so
     * result size is bounded by the number of distinct lessons × terms.
     */
    List<StudentAttendance> findBySchoolIdAndStudentIdOrderByAttendanceDateDescIdDesc(
            Long schoolId, Long studentId);

    @org.springframework.data.jpa.repository.Query("""
            select count(a) > 0 from StudentAttendance a
            join com.peterscode.edutrack.timetable.entity.TimetableLesson l on l.id = a.timetableLessonId
            where a.schoolId = :schoolId and l.schoolId = :schoolId and l.timetableId = :timetableId
            """)
    boolean existsAttendanceForTimetable(@org.springframework.data.repository.query.Param("schoolId") Long schoolId,
                                         @org.springframework.data.repository.query.Param("timetableId") Long timetableId);
}
