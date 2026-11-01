package com.peterscode.edutrack.lms.controller;

import com.peterscode.edutrack.lms.dto.LmsAssignmentRequests;
import com.peterscode.edutrack.lms.dto.LmsViews;
import com.peterscode.edutrack.lms.service.LmsMarkingService;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.security.RequiresModule;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/lms")
@RequiredArgsConstructor
@RequiresModule(ServiceModule.LMS)
public class LmsMarkingController {

    private final LmsMarkingService markingService;

    @PutMapping("/submissions/{submissionId}/mark")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LmsViews.SubmissionView>> markSubmission(
            @PathVariable Long submissionId,
            @Valid @RequestBody LmsAssignmentRequests.MarkSubmissionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        LmsViews.SubmissionView view = markingService.markSubmission(principal.getSchoolId(), submissionId, request, principal);
        return ResponseEntity.ok(ApiResponse.success("Submission marked", view));
    }

    @PostMapping("/submissions/{submissionId}/release")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LmsViews.SubmissionView>> releaseSubmission(
            @PathVariable Long submissionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        LmsViews.SubmissionView view = markingService.releaseSubmission(principal.getSchoolId(), submissionId, principal);
        return ResponseEntity.ok(ApiResponse.success("Score released", view));
    }

    @PostMapping("/assignments/{assignmentId}/release-all")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> releaseAll(
            @PathVariable Long assignmentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        markingService.releaseAll(principal.getSchoolId(), assignmentId, principal);
        return ResponseEntity.ok(ApiResponse.success("All scores released", null));
    }
}
