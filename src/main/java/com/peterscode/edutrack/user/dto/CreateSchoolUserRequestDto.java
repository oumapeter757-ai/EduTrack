package com.peterscode.edutrack.user.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * DTO for SCHOOL_ADMIN creating TEACHER, STUDENT, or PARENT.
 * No schoolId field — the school is inherited from the creating SCHOOL_ADMIN.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CreateSchoolUserRequestDto extends CreateUserRequestDto {
    // School ID is taken from the authenticated SCHOOL_ADMIN's context
}
