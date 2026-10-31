package com.peterscode.edutrack.attendance.controller;

import com.peterscode.edutrack.attendance.dto.AttendanceRequests.BatchRequest;
import com.peterscode.edutrack.attendance.dto.AttendanceRequests.StreamQuery;
import com.peterscode.edutrack.attendance.dto.AttendanceViews.BatchResponse;
import com.peterscode.edutrack.attendance.dto.AttendanceViews.MyStudentAttendanceView;
import com.peterscode.edutrack.attendance.dto.AttendanceViews.StudentAttendanceView;
import com.peterscode.edutrack.attendance.service.AttendanceService;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.security.RequiresModule;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiresModule(ServiceModule.ATTENDANCE)
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {
    private final AttendanceService attendanceService;

    @PostMapping("/batch")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN') or hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<BatchResponse>> recordBatch(
            @Valid @RequestBody BatchRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Attendance recorded",
                attendanceService.recordBatch(request, principal)));
    }

    @GetMapping("/stream")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN') or hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<List<StudentAttendanceView>>> streamAttendance(
            @RequestParam(required = false) Long schoolId,
            @RequestParam Long termId,
            @RequestParam Long streamId,
            @RequestParam LocalDate attendanceDate,
            @RequestParam com.peterscode.edutrack.attendance.entity.AttendanceType attendanceType,
            @RequestParam(required = false) Long timetableLessonId,
            @AuthenticationPrincipal UserPrincipal principal) {
        StreamQuery query = new StreamQuery(schoolId, termId, streamId, attendanceDate, attendanceType, timetableLessonId);
        return ResponseEntity.ok(ApiResponse.success("Attendance retrieved",
                attendanceService.streamAttendance(query, principal)));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<List<MyStudentAttendanceView>>> myAttendance(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Attendance retrieved",
                attendanceService.myAttendance(principal)));
    }
}
