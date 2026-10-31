package com.peterscode.edutrack.attendance.dto;

import com.peterscode.edutrack.attendance.entity.AttendanceStatus;
import com.peterscode.edutrack.attendance.entity.AttendanceType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class AttendanceViews {
    private AttendanceViews() {}

    public record StudentAttendanceView(
            Long id,
            Long studentId,
            String studentName,
            Long streamId,
            LocalDate attendanceDate,
            AttendanceType attendanceType,
            Long timetableLessonId,
            AttendanceStatus status,
            String remarks,
            LocalDateTime updatedAt) {}

    public record BatchResponse(
            int rosterSize,
            int createdCount,
            int updatedCount,
            List<StudentAttendanceView> attendance) {}
}
