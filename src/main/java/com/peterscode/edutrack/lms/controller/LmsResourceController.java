package com.peterscode.edutrack.lms.controller;

import com.peterscode.edutrack.lms.dto.LmsRequests;
import com.peterscode.edutrack.lms.dto.LmsViews;
import com.peterscode.edutrack.lms.service.LmsResourceService;
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
public class LmsResourceController {

    private final LmsResourceService resourceService;

    @PostMapping("/topics/{topicId}/resources")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LmsViews.ResourceView>> createResource(
            @PathVariable Long topicId,
            @Valid @RequestBody LmsRequests.CreateResourceRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        LmsViews.ResourceView view = resourceService.createResource(principal.getSchoolId(), topicId, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Resource created", view));
    }

    @PutMapping("/resources/{resourceId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LmsViews.ResourceView>> updateResource(
            @PathVariable Long resourceId,
            @Valid @RequestBody LmsRequests.UpdateResourceRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        LmsViews.ResourceView view = resourceService.updateResource(principal.getSchoolId(), resourceId, request, principal);
        return ResponseEntity.ok(ApiResponse.success("Resource updated", view));
    }

    @PostMapping("/resources/{resourceId}/publish")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LmsViews.ResourceView>> publishResource(
            @PathVariable Long resourceId,
            @AuthenticationPrincipal UserPrincipal principal) {
        LmsViews.ResourceView view = resourceService.publishResource(principal.getSchoolId(), resourceId, principal);
        return ResponseEntity.ok(ApiResponse.success("Resource published", view));
    }

    @GetMapping("/topics/{topicId}/resources")
    @PreAuthorize("hasAnyRole('TEACHER', 'STUDENT', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<PageResponse<LmsViews.ResourceView>> listResources(
            @PathVariable Long topicId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        var page = resourceService.listResources(principal.getSchoolId(), topicId, pageable, principal);
        return ResponseEntity.ok(PageResponse.from(page));
    }
}
