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
}
