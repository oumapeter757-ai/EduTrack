package com.peterscode.edutrack.user.controller;

import com.peterscode.edutrack.auth.dto.AdminChangePasswordRequest;
import com.peterscode.edutrack.auth.service.PasswordManagementService;
import com.peterscode.edutrack.security.NoModuleRequired;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@NoModuleRequired
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class UserPasswordController {

    private final PasswordManagementService passwordManagementService;

    @PatchMapping("/{userId}/password")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> changeUserPassword(
            @PathVariable Long userId,
            @Valid @RequestBody AdminChangePasswordRequest request,
            @AuthenticationPrincipal UserPrincipal administrator) {
        passwordManagementService.adminChangePassword(administrator, userId, request.newPassword());
        return ResponseEntity.ok(ApiResponse.success("User password changed. Their existing sessions were revoked."));
    }
}
