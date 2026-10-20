package com.peterscode.edutrack.school.controller;

import com.peterscode.edutrack.school.dto.*;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import com.peterscode.edutrack.school.service.SchoolService;
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

import java.util.Set;

@RestController
@NoModuleRequired
@RequestMapping("/api/v1/schools")
@RequiredArgsConstructor
public class SchoolController {

    private final SchoolService schoolService;

    /**
     * SUPER_ADMIN creates a new tenant school.
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<SchoolResponseDto>> createSchool(
            @Valid @RequestBody CreateSchoolRequestDto requestDto) {
        SchoolResponseDto response = schoolService.createSchool(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("School created successfully", response));
    }

    /**
     * Full school update (SUPER_ADMIN or SCHOOL_ADMIN of this school).
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<SchoolResponseDto>> updateSchool(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSchoolRequestDto requestDto) {
        SchoolResponseDto response = schoolService.updateSchool(id, requestDto);
        return ResponseEntity.ok(ApiResponse.success("School updated successfully", response));
    }

    /**
     * Update school profile (contact, address, branding, localization).
     */
    @PutMapping("/{id}/profile")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<SchoolResponseDto>> updateSchoolProfile(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSchoolProfileRequestDto requestDto,
            @AuthenticationPrincipal UserPrincipal principal) {
        SchoolResponseDto response = schoolService.updateSchoolProfile(id, requestDto, principal);
        return ResponseEntity.ok(ApiResponse.success("School profile updated successfully", response));
    }

    /**
     * Update school subscription plan, module entitlements, and capacity quotas (SUPER_ADMIN only).
     */
    @PutMapping("/{id}/subscription")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<SchoolResponseDto>> updateSchoolSubscription(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSchoolSubscriptionRequestDto requestDto) {
        SchoolResponseDto response = schoolService.updateSchoolSubscription(id, requestDto);
        return ResponseEntity.ok(ApiResponse.success("School subscription updated successfully", response));
    }

    /**
     * Retrieve the currently logged-in user's school profile.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SchoolResponseDto>> getCurrentSchool(
            @AuthenticationPrincipal UserPrincipal principal) {
        SchoolResponseDto response = schoolService.getCurrentSchool(principal.getSchoolId());
        return ResponseEntity.ok(ApiResponse.success("Current school retrieved successfully", response));
    }

    /**
     * Retrieve school details by ID (SUPER_ADMIN or any authenticated user of this school).
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SchoolResponseDto>> getSchoolById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        SchoolResponseDto response = schoolService.getSchoolById(id, principal);
        return ResponseEntity.ok(ApiResponse.success("School retrieved successfully", response));
    }

    /**
     * Retrieve school details by unique school code.
     */
    @GetMapping("/code/{code}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<PublicSchoolProfileDto>> getSchoolByCode(
            @PathVariable String code) {
        PublicSchoolProfileDto response = schoolService.getSchoolByCode(code);
        return ResponseEntity.ok(ApiResponse.success("School retrieved successfully", response));
    }

    /**
     * Public tenant lookup by subdomain for login and brand resolution (permitAll).
     */
    @GetMapping("/subdomain/{subdomain}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<PublicSchoolProfileDto>> getSchoolBySubdomain(
            @PathVariable String subdomain) {
        PublicSchoolProfileDto response = schoolService.getPublicSchoolBySubdomain(subdomain);
        return ResponseEntity.ok(ApiResponse.success("School profile retrieved successfully", response));
    }

    /**
     * List all schools with search, tier, and status filters (SUPER_ADMIN only).
     */
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<SchoolResponseDto>>> getAllSchools(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SubscriptionTier tier,
            @RequestParam(required = false) Boolean active,
            Pageable pageable) {
        PageResponse<SchoolResponseDto> response = schoolService.getAllSchools(search, tier, active, pageable);
        return ResponseEntity.ok(ApiResponse.success("Schools retrieved successfully", response));
    }

    /**
     * Toggle active/inactive status of a school (SUPER_ADMIN only).
     */
    @PutMapping("/toggle-status/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> toggleSchoolStatus(
            @PathVariable Long id,
            @RequestParam boolean active) {
        schoolService.toggleSchoolStatus(id, active);
        String statusText = active ? "activated" : "deactivated";
        return ResponseEntity.ok(ApiResponse.success("School " + statusText + " successfully", null));
    }

    /**
     * Alternative PATCH endpoint to activate/deactivate a school (SUPER_ADMIN only).
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateSchoolStatus(
            @PathVariable Long id,
            @RequestParam boolean active) {
        schoolService.toggleSchoolStatus(id, active);
        String statusText = active ? "activated" : "deactivated";
        return ResponseEntity.ok(ApiResponse.success("School " + statusText + " successfully", null));
    }

    /**
     * Update enabled enterprise service modules for a school (SUPER_ADMIN only).
     */
    @PutMapping("/{id}/modules")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<SchoolResponseDto>> updateSchoolModules(
            @PathVariable Long id,
            @RequestBody Set<ServiceModule> modules) {
        SchoolResponseDto response = schoolService.updateEnabledModules(id, modules);
        return ResponseEntity.ok(ApiResponse.success("School service modules updated successfully", response));
    }
}
