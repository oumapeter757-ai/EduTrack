package com.peterscode.edutrack.user.controller;

import com.peterscode.edutrack.user.dto.CreateSchoolAdminRequestDto;
import com.peterscode.edutrack.user.dto.CreateSchoolUserRequestDto;
import com.peterscode.edutrack.user.dto.CreateSuperAdminRequestDto;
import com.peterscode.edutrack.user.dto.UserResponseDto;
import com.peterscode.edutrack.shared.response.ApiResponse;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.user.service.UserRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for hierarchical user creation.
 * <p>
 * Endpoints are role-specific — the role to be created is determined by
 * which endpoint is called, NOT by client input.
 * <p>
 * <ul>
 *   <li>POST /api/v1/admin/super-admins — SUPER_ADMIN creates SUPER_ADMIN</li>
 *   <li>POST /api/v1/admin/school-admins — SUPER_ADMIN creates SCHOOL_ADMIN</li>
 *   <li>POST /api/v1/admin/teachers — SCHOOL_ADMIN creates TEACHER</li>
 *   <li>POST /api/v1/admin/students — SCHOOL_ADMIN creates STUDENT</li>
 *   <li>POST /api/v1/admin/parents — SCHOOL_ADMIN creates PARENT</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class UserManagementController {

    private final UserRegistrationService registrationService;

    /**
     * SUPER_ADMIN creates another SUPER_ADMIN.
     */
    @PostMapping("/super-admins")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponseDto>> createSuperAdmin(
            @Valid @RequestBody CreateSuperAdminRequestDto request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        UserResponseDto response = registrationService.createSuperAdmin(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Super admin created successfully", response));
    }

    /**
     * SUPER_ADMIN creates a SCHOOL_ADMIN for any school.
     */
    @PostMapping("/school-admins")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponseDto>> createSchoolAdmin(
            @Valid @RequestBody CreateSchoolAdminRequestDto request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        UserResponseDto response = registrationService.createSchoolAdmin(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("School admin created successfully", response));
    }

    /**
     * SCHOOL_ADMIN creates a TEACHER for their own school.
     */
    @PostMapping("/teachers")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponseDto>> createTeacher(
            @Valid @RequestBody CreateSchoolUserRequestDto request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        UserResponseDto response = registrationService.createTeacher(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Teacher created successfully", response));
    }

    /**
     * SCHOOL_ADMIN creates a STUDENT for their own school.
     */
    @PostMapping("/students")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponseDto>> createStudent(
            @Valid @RequestBody CreateSchoolUserRequestDto request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        UserResponseDto response = registrationService.createStudent(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Student created successfully", response));
    }

    /**
     * SCHOOL_ADMIN creates a PARENT for their own school.
     */
    @PostMapping("/parents")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponseDto>> createParent(
            @Valid @RequestBody CreateSchoolUserRequestDto request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        UserResponseDto response = registrationService.createParent(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Parent created successfully", response));
    }
}
