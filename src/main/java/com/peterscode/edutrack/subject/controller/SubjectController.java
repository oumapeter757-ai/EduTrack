package com.peterscode.edutrack.subject.controller;

import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.ApiResponse;
import com.peterscode.edutrack.shared.response.PageResponse;
import com.peterscode.edutrack.subject.dto.*;
import com.peterscode.edutrack.subject.service.SubjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.peterscode.edutrack.security.RequiresModule;
import com.peterscode.edutrack.school.enums.ServiceModule;

import java.util.List;

@RestController
@RequiresModule(ServiceModule.GRADING)
@RequestMapping("/api/v1/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<SubjectResponseDto>> createSubject(
            @Valid @RequestBody CreateSubjectRequestDto requestDto,
            @AuthenticationPrincipal UserPrincipal principal) {
        SubjectResponseDto response = subjectService.createSubject(requestDto, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Subject created successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<PageResponse<SubjectResponseDto>>> getSubjects(
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Long classLevelId,
            @RequestParam(required = false) String department,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        PageResponse<SubjectResponseDto> response = subjectService.getSubjects(schoolId, classLevelId, department, pageable, principal);
        return ResponseEntity.ok(ApiResponse.success("Subjects retrieved successfully", response));
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<SubjectSummaryDto>>> getActiveSubjects(
            @RequestParam(required = false) Long schoolId,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<SubjectSummaryDto> response = subjectService.getActiveSubjects(schoolId, principal);
        return ResponseEntity.ok(ApiResponse.success("Active subjects retrieved successfully", response));
    }

    @GetMapping("/class-level/{classLevelId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<SubjectResponseDto>>> getSubjectsByClassLevel(
            @PathVariable Long classLevelId,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<SubjectResponseDto> response = subjectService.getSubjectsByClassLevel(classLevelId, principal);
        return ResponseEntity.ok(ApiResponse.success("Subjects retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SubjectResponseDto>> getSubjectById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        SubjectResponseDto response = subjectService.getSubjectById(id, principal);
        return ResponseEntity.ok(ApiResponse.success("Subject retrieved successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<SubjectResponseDto>> updateSubject(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSubjectRequestDto requestDto,
            @AuthenticationPrincipal UserPrincipal principal) {
        SubjectResponseDto response = subjectService.updateSubject(id, requestDto, principal);
        return ResponseEntity.ok(ApiResponse.success("Subject updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteSubject(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        subjectService.deleteSubject(id, principal);
        return ResponseEntity.ok(ApiResponse.success("Subject deleted successfully", null));
    }
}
