package com.peterscode.edutrack.lms.controller;

import com.peterscode.edutrack.lms.dto.LmsAssignmentRequests;
import com.peterscode.edutrack.lms.dto.LmsViews;
import com.peterscode.edutrack.lms.service.LmsAssignmentService;
import com.peterscode.edutrack.lms.service.LmsSubmissionService;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.security.RequiresModule;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.ApiResponse;
import com.peterscode.edutrack.shared.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/lms")
@RequiredArgsConstructor
@RequiresModule(ServiceModule.LMS)
public class LmsAssignmentController {

    private final LmsAssignmentService assignmentService;

    @PostMapping("/courses/{courseId}/assignments")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LmsViews.AssignmentView>> createAssignment(
            @PathVariable Long courseId,
            @Valid @RequestBody LmsAssignmentRequests.CreateAssignmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        LmsViews.AssignmentView view = assignmentService.createAssignment(principal.getSchoolId(), courseId, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Assignment created", view));
    }

    @PostMapping("/assignments/{assignmentId}/publish")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LmsViews.AssignmentView>> publishAssignment(
            @PathVariable Long assignmentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        LmsViews.AssignmentView view = assignmentService.publishAssignment(principal.getSchoolId(), assignmentId, principal);
        return ResponseEntity.ok(ApiResponse.success("Assignment published", view));
    }

    @PostMapping("/assignments/{assignmentId}/close")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LmsViews.AssignmentView>> closeAssignment(
            @PathVariable Long assignmentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        LmsViews.AssignmentView view = assignmentService.closeAssignment(principal.getSchoolId(), assignmentId, principal);
        return ResponseEntity.ok(ApiResponse.success("Assignment closed", view));
    }

    @GetMapping("/courses/{courseId}/assignments")
    @PreAuthorize("hasAnyRole('TEACHER', 'STUDENT', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<PageResponse<LmsViews.AssignmentView>> listAssignments(
            @PathVariable Long courseId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        var page = assignmentService.listAssignments(principal.getSchoolId(), courseId, pageable, principal);
        return ResponseEntity.ok(PageResponse.from(page));
    }
}
