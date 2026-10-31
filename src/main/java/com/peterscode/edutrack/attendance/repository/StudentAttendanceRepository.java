package com.peterscode.edutrack.attendance.repository;

import com.peterscode.edutrack.attendance.entity.AttendanceType;
import com.peterscode.edutrack.attendance.entity.StudentAttendance;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentAttendanceRepository extends JpaRepository<StudentAttendance, Long> {
    List<StudentAttendance> findBySchoolIdAndTermIdAndStreamIdAndAttendanceDateAndAttendanceTypeAndTimetableLessonId(
            Long schoolId, Long termId, Long streamId, LocalDate attendanceDate,
            AttendanceType attendanceType, Long timetableLessonId);

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
