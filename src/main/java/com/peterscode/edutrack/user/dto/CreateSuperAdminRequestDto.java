package com.peterscode.edutrack.user.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * DTO for creating a SUPER_ADMIN.
 * No schoolId field — SUPER_ADMIN is platform-wide (schoolId is set to 0).
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CreateSuperAdminRequestDto extends CreateUserRequestDto {
}
