package com.peterscode.edutrack.enrollment.controller;

import com.peterscode.edutrack.enrollment.dto.EnrollmentView;
import com.peterscode.edutrack.enrollment.service.EnrollmentService;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.security.RequiresModule;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.*;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequiresModule(ServiceModule.STUDENT_MANAGEMENT)
@RequestMapping("/api/v1/enrollments")
public class EnrollmentController {
    private final EnrollmentService enrollmentService;

    @GetMapping("/students/{studentId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAnyRole('SCHOOL_ADMIN','STUDENT','PARENT')")
    public ResponseEntity<ApiResponse<PageResponse<EnrollmentView>>> history(
            @PathVariable Long studentId, @RequestParam(required = false) Long schoolId, Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Enrollment history retrieved successfully",
                enrollmentService.history(studentId, schoolId, pageable, principal)));
    }
}
