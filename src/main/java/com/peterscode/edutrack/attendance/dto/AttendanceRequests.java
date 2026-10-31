package com.peterscode.edutrack.attendance.dto;

import com.peterscode.edutrack.attendance.entity.AttendanceStatus;
import com.peterscode.edutrack.attendance.entity.AttendanceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public final class AttendanceRequests {
    private AttendanceRequests() {}

    public record BatchRequest(
            Long schoolId,
            @NotNull Long termId,
            @NotNull Long streamId,
            @NotNull LocalDate attendanceDate,
            @NotNull AttendanceType attendanceType,
            Long timetableLessonId,
            @Size(max = 2000) List<@Valid StatusException> exceptions) {}

    public record StatusException(
            @NotNull Long studentId,
            @NotNull AttendanceStatus status,
            @Size(max = 500) String remarks) {}

    public record StreamQuery(
            Long schoolId,
            @NotNull Long termId,
            @NotNull Long streamId,
            @NotNull LocalDate attendanceDate,
            @NotNull AttendanceType attendanceType,
            Long timetableLessonId) {}
}
