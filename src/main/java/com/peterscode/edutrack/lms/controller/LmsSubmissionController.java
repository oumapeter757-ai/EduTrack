package com.peterscode.edutrack.lms.controller;

import com.peterscode.edutrack.lms.dto.LmsAssignmentRequests;
import com.peterscode.edutrack.lms.dto.LmsViews;
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
public class LmsSubmissionController {

    private final LmsSubmissionService submissionService;

    @PostMapping("/assignments/{assignmentId}/submissions")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<LmsViews.SubmissionView>> submit(
            @PathVariable Long assignmentId,
            @Valid @RequestBody LmsAssignmentRequests.SubmitAssignmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        LmsViews.SubmissionView view = submissionService.submit(principal.getSchoolId(), assignmentId, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Submission created", view));
    }

    @GetMapping("/submissions/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<PageResponse<LmsViews.SubmissionView>> listMySubmissions(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        var page = submissionService.listMySubmissions(principal.getSchoolId(), pageable, principal);
        return ResponseEntity.ok(PageResponse.from(page));
    }

    @GetMapping("/assignments/{assignmentId}/submissions")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<PageResponse<LmsViews.SubmissionView>> listCourseSubmissions(
            @PathVariable Long assignmentId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        var page = submissionService.listCourseSubmissions(principal.getSchoolId(), assignmentId, pageable, principal);
        return ResponseEntity.ok(PageResponse.from(page));
    }

    @GetMapping("/submissions/{submissionId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LmsViews.SubmissionView>> getSubmission(
            @PathVariable Long submissionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        LmsViews.SubmissionView view = submissionService.getSubmission(principal.getSchoolId(), submissionId, principal);
        return ResponseEntity.ok(ApiResponse.success("Submission", view));
    }
}
