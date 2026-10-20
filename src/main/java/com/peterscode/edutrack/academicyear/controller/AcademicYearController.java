package com.peterscode.edutrack.academicyear.controller;

import com.peterscode.edutrack.academicyear.dto.AcademicYearResponseDto;
import com.peterscode.edutrack.academicyear.dto.CreateAcademicYearRequestDto;
import com.peterscode.edutrack.academicyear.dto.UpdateAcademicYearRequestDto;
import com.peterscode.edutrack.academicyear.service.AcademicYearService;
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

@RestController
@NoModuleRequired
@RequestMapping("/api/v1/academic-years")
@RequiredArgsConstructor
public class AcademicYearController {

    private final AcademicYearService academicYearService;

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<AcademicYearResponseDto>> createAcademicYear(
            @Valid @RequestBody CreateAcademicYearRequestDto requestDto,
            @AuthenticationPrincipal UserPrincipal principal) {
        AcademicYearResponseDto response = academicYearService.createAcademicYear(requestDto, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Academic year created successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<PageResponse<AcademicYearResponseDto>>> getAcademicYears(
            @RequestParam(required = false) Long schoolId,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        PageResponse<AcademicYearResponseDto> response = academicYearService.getAcademicYears(schoolId, pageable, principal);
        return ResponseEntity.ok(ApiResponse.success("Academic years retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AcademicYearResponseDto>> getAcademicYearById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        AcademicYearResponseDto response = academicYearService.getAcademicYearById(id, principal);
        return ResponseEntity.ok(ApiResponse.success("Academic year retrieved successfully", response));
    }

    @GetMapping("/current")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AcademicYearResponseDto>> getCurrentAcademicYear(
            @AuthenticationPrincipal UserPrincipal principal) {
        AcademicYearResponseDto response = academicYearService.getCurrentAcademicYear(principal);
        return ResponseEntity.ok(ApiResponse.success("Current academic year retrieved successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<AcademicYearResponseDto>> updateAcademicYear(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAcademicYearRequestDto requestDto,
            @AuthenticationPrincipal UserPrincipal principal) {
        AcademicYearResponseDto response = academicYearService.updateAcademicYear(id, requestDto, principal);
        return ResponseEntity.ok(ApiResponse.success("Academic year updated successfully", response));
    }

    @PatchMapping("/{id}/set-current")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<AcademicYearResponseDto>> setCurrentAcademicYear(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        AcademicYearResponseDto response = academicYearService.setCurrentAcademicYear(id, principal);
        return ResponseEntity.ok(ApiResponse.success("Academic year set as current successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteAcademicYear(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        academicYearService.deleteAcademicYear(id, principal);
        return ResponseEntity.ok(ApiResponse.success("Academic year deleted successfully", null));
    }
}
