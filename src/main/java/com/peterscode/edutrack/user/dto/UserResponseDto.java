package com.peterscode.edutrack.user.dto;

import com.peterscode.edutrack.user.entity.Role;
import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for user creation operations (no token, just user info).
 */
@Data
@Builder
public class UserResponseDto {
    private Long id;
    private String firstName;
    private String lastName;
    private String username;
    private String email;
    private Long schoolId;
    private Role role;
    private boolean active;
}
