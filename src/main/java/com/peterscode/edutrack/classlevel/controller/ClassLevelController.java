package com.peterscode.edutrack.classlevel.controller;

import com.peterscode.edutrack.classlevel.dto.*;
import com.peterscode.edutrack.classlevel.service.ClassLevelService;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.ApiResponse;
import com.peterscode.edutrack.shared.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.peterscode.edutrack.security.NoModuleRequired;

import java.util.List;

@RestController
@NoModuleRequired
@RequestMapping("/api/v1/class-levels")
@RequiredArgsConstructor
public class ClassLevelController {

    private final ClassLevelService classLevelService;

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<ClassLevelResponseDto>> createClassLevel(
            @Valid @RequestBody CreateClassLevelRequestDto requestDto,
            @AuthenticationPrincipal UserPrincipal principal) {
        ClassLevelResponseDto response = classLevelService.createClassLevel(requestDto, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Class level created successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<PageResponse<ClassLevelResponseDto>>> getClassLevels(
            @RequestParam(required = false) Long schoolId, Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        PageResponse<ClassLevelResponseDto> response = classLevelService.getClassLevels(schoolId, pageable, principal);
        return ResponseEntity.ok(ApiResponse.success("Class levels retrieved successfully", response));
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ClassLevelResponseDto>>> getActiveClassLevels(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<ClassLevelResponseDto> response = classLevelService.getActiveClassLevels(principal);
        return ResponseEntity.ok(ApiResponse.success("Active class levels retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ClassLevelResponseDto>> getClassLevelById(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        ClassLevelResponseDto response = classLevelService.getClassLevelById(id, principal);
        return ResponseEntity.ok(ApiResponse.success("Class level retrieved successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<ClassLevelResponseDto>> updateClassLevel(
            @PathVariable Long id, @Valid @RequestBody UpdateClassLevelRequestDto requestDto,
            @AuthenticationPrincipal UserPrincipal principal) {
        ClassLevelResponseDto response = classLevelService.updateClassLevel(id, requestDto, principal);
        return ResponseEntity.ok(ApiResponse.success("Class level updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteClassLevel(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        classLevelService.deleteClassLevel(id, principal);
        return ResponseEntity.ok(ApiResponse.success("Class level deleted successfully", null));
    }
}
