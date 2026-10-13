package com.peterscode.edutrack.security;

import com.peterscode.edutrack.user.entity.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("securityUtils")
public class SecurityUtils {

    /**
     * Checks if the currently authenticated user belongs to the requested tenant school ID.
     * Evaluated dynamically in SpEL expressions: @securityUtils.isCurrentTenant(#id)
     */
    public boolean isCurrentTenant(Long schoolId) {
        if (schoolId == null) {
            return false;
        }

        Long currentSchoolId = getCurrentSchoolId();
        return currentSchoolId != null && currentSchoolId.equals(schoolId);
    }

    /**
     * Checks if the currently authenticated user is a SUPER_ADMIN.
     */
    public boolean isSuperAdmin() {
        UserPrincipal principal = getCurrentPrincipal();
        return principal != null && principal.getRole() == Role.SUPER_ADMIN;
    }

    /**
     * Retrieves the school ID (tenant boundary) of the currently logged-in user.
     */
    public Long getCurrentSchoolId() {
        UserPrincipal principal = getCurrentPrincipal();
        return principal != null ? principal.getSchoolId() : null;
    }

    /**
     * Retrieves the database ID of the currently logged-in user.
     */
    public Long getCurrentUserId() {
        UserPrincipal principal = getCurrentPrincipal();
        return principal != null ? principal.getId() : null;
    }

    /**
     * Extracts our custom UserPrincipal from Spring Security's SecurityContextHolder.
     */
    public UserPrincipal getCurrentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }
}