package com.peterscode.edutrack.auth.controller;

import com.peterscode.edutrack.auth.dto.AuthResponseDto;
import com.peterscode.edutrack.auth.dto.LoginRequestDto;
import com.peterscode.edutrack.auth.service.AuthService;
import com.peterscode.edutrack.shared.response.ApiResponse;
import com.peterscode.edutrack.user.dto.CreateSuperAdminRequestDto;
import com.peterscode.edutrack.user.dto.UserResponseDto;
import com.peterscode.edutrack.user.service.UserRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.peterscode.edutrack.security.NoModuleRequired;

/**
 * Authentication controller for login and initial super admin signup.
 * <p>
 * Initial SUPER_ADMIN signup is open only when no SUPER_ADMIN exists in the system.
 * Once a SUPER_ADMIN exists, signup is disabled.
 * Hierarchical user creation is handled by {@link com.peterscode.edutrack.user.controller.UserManagementController}.
 */
@RestController
@NoModuleRequired
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRegistrationService registrationService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponseDto>> login(@Valid @RequestBody LoginRequestDto request) {
        AuthResponseDto response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Authentication successful", response));
    }


    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponseDto>> signup(
            @RequestHeader(value = "X-Bootstrap-Token", required = false) String bootstrapToken,
            @Valid @RequestBody CreateSuperAdminRequestDto request) {
        UserResponseDto response = registrationService.signupInitialSuperAdmin(request, bootstrapToken);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Super admin registered successfully", response));
    }
}