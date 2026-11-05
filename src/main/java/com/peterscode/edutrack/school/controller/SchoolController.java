package com.peterscode.edutrack.school.controller;

import com.peterscode.edutrack.school.dto.*;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import com.peterscode.edutrack.school.service.SchoolService;
import com.peterscode.edutrack.security.ModuleAccessService;
import com.peterscode.edutrack.security.SchoolEntitlements;
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
    private final ModuleAccessService moduleAccessService;

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
     * Update a school's routing and profile fields (SUPER_ADMIN only).
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
     * Update school profile fields for the school itself or a SUPER_ADMIN.
     */
    @PutMapping("/{id}/profile")
    @PreAuthorize("hasRole('SUPER_ADMIN') or (hasRole('SCHOOL_ADMIN') and @securityUtils.isCurrentTenant(#id))")
    public ResponseEntity<ApiResponse<SchoolResponseDto>> updateSchoolProfile(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSchoolProfileRequestDto requestDto,
            @AuthenticationPrincipal UserPrincipal principal) {
        SchoolResponseDto response = schoolService.updateSchoolProfile(id, requestDto, principal);
        return ResponseEntity.ok(ApiResponse.success("School profile updated successfully", response));
    }

    /**
     * Change subscription settings and limits (SUPER_ADMIN only).
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
     * Retrieve the authenticated user's own school.
     */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER', 'STUDENT', 'PARENT', 'LIBRARIAN', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse<SchoolResponseDto>> getCurrentSchool(
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal.getSchoolId() == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("User has no associated school"));
        }
        SchoolResponseDto response = schoolService.getCurrentSchool(principal.getSchoolId());
        return ResponseEntity.ok(ApiResponse.success("Current school retrieved successfully", response));
    }

    /**
     * Retrieve school details only for its own tenant or a SUPER_ADMIN.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER', 'STUDENT', 'PARENT', 'LIBRARIAN', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse<SchoolResponseDto>> getSchoolById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        SchoolResponseDto response = schoolService.getSchoolById(id, principal);
        return ResponseEntity.ok(ApiResponse.success("School retrieved successfully", response));
    }

    /**
     * Public school-code lookup for login and branding.
     */
    @GetMapping("/code/{code}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<PublicSchoolProfileDto>> getSchoolByCode(
            @PathVariable String code) {
        PublicSchoolProfileDto response = schoolService.getSchoolByCode(code);
        return ResponseEntity.ok(ApiResponse.success("School retrieved successfully", response));
    }

    /**
     * Public subdomain lookup for login and branding.
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

    /**
     * Get subscription entitlements for the current user's school.
     * <p>
     * Any authenticated school user can view their school's entitlements.
     * SUPER_ADMIN with no school context gets a safe empty response.
     */
    @GetMapping("/me/entitlements")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER', 'STUDENT', 'PARENT', 'LIBRARIAN', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse<SchoolEntitlementsDto>> getEntitlements(
            @AuthenticationPrincipal UserPrincipal principal) {

        if (principal.getSchoolId() == null) {
            SchoolEntitlementsDto empty = SchoolEntitlementsDto.builder().build();
            return ResponseEntity.ok(ApiResponse.success("No school context", empty));
        }

        SchoolEntitlements entitlements = moduleAccessService.entitlements(principal.getSchoolId());
        SchoolEntitlementsDto dto = SchoolEntitlementsDto.builder()
                .tier(entitlements.tier())
                .storedStatus(entitlements.storedStatus())
                .effectiveStatus(entitlements.status())
                .endDate(entitlements.endDate())
                .daysLeft(entitlements.daysLeft())
                .enabledModules(entitlements.enabledModules())
                .tierModules(entitlements.tierModules())
                .active(entitlements.active())
                .build();

        return ResponseEntity.ok(ApiResponse.success("Entitlements retrieved successfully", dto));
    }
}
