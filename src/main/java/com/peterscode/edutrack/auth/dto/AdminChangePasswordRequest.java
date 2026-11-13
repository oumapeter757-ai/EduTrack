package com.peterscode.edutrack.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminChangePasswordRequest(
        @NotBlank @Size(min = 8, max = 128) String newPassword) {
}
