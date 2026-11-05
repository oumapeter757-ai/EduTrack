package com.peterscode.edutrack.enrollment.controller;

import com.peterscode.edutrack.enrollment.entity.EnrollmentSettings;
import com.peterscode.edutrack.enrollment.dto.EnrollmentSettingsRequest;
import com.peterscode.edutrack.enrollment.service.EnrollmentSettingsService;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.security.*;
import com.peterscode.edutrack.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequiresModule(ServiceModule.STUDENT_MANAGEMENT)
@RequestMapping("/api/v1/enrollment/settings")
public class EnrollmentSettingsController {
    private final EnrollmentSettingsService service;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<EnrollmentSettings>> get(
            @RequestParam Long schoolId, @AuthenticationPrincipal UserPrincipal principal) {
        requireSchool(schoolId, principal);
        return ResponseEntity.ok(ApiResponse.success("Enrollment settings retrieved", service.getOrCreate(schoolId)));
    }

    @PutMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<EnrollmentSettings>> save(
            @RequestParam Long schoolId, @Valid @RequestBody EnrollmentSettingsRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireSchool(schoolId, principal);
        EnrollmentSettings settings = service.getOrCreate(schoolId);
        settings.setSuggestedMinAverage(request.suggestedMinAverage());
        settings.setBlockOnUnpaidFees(request.blockOnUnpaidFees());
        settings.setBlockOnOpenLibraryLoans(request.blockOnOpenLibraryLoans());
        settings.setRequireClosedTerm(request.requireClosedTerm());
        settings.setUndoWindowDays(request.undoWindowDays());
        settings.setAlumniLoginDays(request.alumniLoginDays());
        settings.setBatchSize(request.batchSize());
        return ResponseEntity.ok(ApiResponse.success("Enrollment settings saved", service.save(settings)));
    }

    private void requireSchool(Long schoolId, UserPrincipal principal) {
        if (principal == null || (principal.getRole() != com.peterscode.edutrack.user.entity.Role.SUPER_ADMIN
                && !schoolId.equals(principal.getSchoolId()))) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }
    }
}
