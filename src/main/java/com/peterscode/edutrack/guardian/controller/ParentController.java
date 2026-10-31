package com.peterscode.edutrack.guardian.controller;

import com.peterscode.edutrack.guardian.dto.CreateParentRequestDto;
import com.peterscode.edutrack.guardian.dto.LinkChildRequestDto;
import com.peterscode.edutrack.guardian.dto.ParentChildSummary;
import com.peterscode.edutrack.guardian.dto.ParentResponseDto;
import com.peterscode.edutrack.guardian.service.ParentService;
import com.peterscode.edutrack.security.NoModuleRequired;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@NoModuleRequired
@RequestMapping("/api/v1/parents")
@RequiredArgsConstructor
public class ParentController {

    private final ParentService parentService;

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<ParentResponseDto>> createParent(
            @Valid @RequestBody CreateParentRequestDto request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ParentResponseDto response = parentService.createParentWithChildren(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Parent created successfully", response));
    }

    @GetMapping("/me/children")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ApiResponse<List<ParentChildSummary>>> myChildren(
            @AuthenticationPrincipal UserPrincipal principal) {
        Long schoolId = principal.getSchoolId();
        if (schoolId == null) {
            return ResponseEntity.ok(ApiResponse.success("No children found", List.of()));
        }
        List<ParentChildSummary> children = parentService.myChildren(schoolId, principal);
        return ResponseEntity.ok(ApiResponse.success("Children retrieved successfully", children));
    }

    @PostMapping("/{guardianId}/link-child")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> linkChild(
            @PathVariable Long guardianId,
            @Valid @RequestBody LinkChildRequestDto request,
            @AuthenticationPrincipal UserPrincipal principal) {
        parentService.linkChild(guardianId, request, principal);
        return ResponseEntity.ok(ApiResponse.success("Child linked successfully", null));
    }

    @DeleteMapping("/{guardianId}/unlink-child/{studentId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> unlinkChild(
            @PathVariable Long guardianId,
            @PathVariable Long studentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        parentService.unlinkChild(guardianId, studentId, principal);
        return ResponseEntity.ok(ApiResponse.success("Child unlinked successfully", null));
    }

    @GetMapping("/{guardianId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<ParentService.GuardianResponse>> getGuardian(
            @PathVariable Long guardianId,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long schoolId = principal.getSchoolId();
        if (schoolId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("School ID is required for SUPER_ADMIN"));
        }
        ParentService.GuardianResponse response = parentService.getGuardian(schoolId, guardianId, principal);
        return ResponseEntity.ok(ApiResponse.success("Guardian retrieved successfully", response));
    }
}
