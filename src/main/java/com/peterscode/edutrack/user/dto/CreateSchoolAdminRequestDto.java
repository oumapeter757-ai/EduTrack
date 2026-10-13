package com.peterscode.edutrack.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * DTO for SUPER_ADMIN creating a SCHOOL_ADMIN.
 * Requires schoolId since SUPER_ADMIN can create admins for any school.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CreateSchoolAdminRequestDto extends CreateUserRequestDto {

    @NotNull(message = "School ID is required")
    private Long schoolId;
}
