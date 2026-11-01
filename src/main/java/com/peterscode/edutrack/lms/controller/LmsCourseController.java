package com.peterscode.edutrack.lms.controller;

import com.peterscode.edutrack.lms.dto.LmsRequests;
import com.peterscode.edutrack.lms.dto.LmsViews;
import com.peterscode.edutrack.lms.service.LmsCourseService;
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
public class LmsCourseController {

    private final LmsCourseService courseService;

    @PostMapping("/courses")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<LmsViews.CourseView>> createCourse(
            @Valid @RequestBody LmsRequests.CreateCourseRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        LmsViews.CourseView view = courseService.createCourse(principal.getSchoolId(), request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Course created", view));
    }

    @PutMapping("/courses/{courseId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LmsViews.CourseView>> updateCourse(
            @PathVariable Long courseId,
            @Valid @RequestBody LmsRequests.UpdateCourseRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        LmsViews.CourseView view = courseService.updateCourse(principal.getSchoolId(), courseId, request, principal);
        return ResponseEntity.ok(ApiResponse.success("Course updated", view));
    }

    @GetMapping("/courses/me")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<PageResponse<LmsViews.CourseView>> listMyCourses(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        var page = courseService.listMyCourses(principal.getSchoolId(), pageable, principal);
        return ResponseEntity.ok(PageResponse.from(page));
    }

    @GetMapping("/courses/{courseId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'STUDENT', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LmsViews.CourseView>> getCourse(
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserPrincipal principal) {
        LmsViews.CourseView view = courseService.getCourse(principal.getSchoolId(), courseId, principal);
        return ResponseEntity.ok(ApiResponse.success("Course", view));
    }
}
