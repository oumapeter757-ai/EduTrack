package com.peterscode.edutrack.fees.security;

import com.peterscode.edutrack.fees.exception.FeesRuleException;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.user.entity.Role;
import org.springframework.http.HttpStatus;

/**
 * Centralized authorization decisions for the fees module.
 * <p>
 * Rules:
 * <ul>
 *   <li>SCHOOL_ADMIN and ACCOUNTANT: full school-wide fee access</li>
 *   <li>PARENT: access only to linked children's fees</li>
 *   <li>STUDENT: access only to their own fees</li>
 *   <li>TEACHER, LIBRARIAN: no access to fee data</li>
 * </ul>
 */
public final class FeesAccessGuard {

    private FeesAccessGuard() {}

    /**
     * Assert the caller is SCHOOL_ADMIN or ACCOUNTANT for the given school.
     */
    public static void assertAdminOrAccountant(UserPrincipal principal) {
        if (principal == null) {
            throw new FeesRuleException("INSUFFICIENT_PERMISSION", HttpStatus.FORBIDDEN,
                    "Authentication required");
        }
        Role role = principal.getRole();
        if (role != Role.SCHOOL_ADMIN && role != Role.ACCOUNTANT) {
            throw new FeesRuleException("INSUFFICIENT_PERMISSION", HttpStatus.FORBIDDEN,
                    "Only School Admin or Accountant can perform this action");
        }
    }

    /**
     * Assert the caller is SCHOOL_ADMIN, ACCOUNTANT, or SUPER_ADMIN.
     */
    public static void assertAdmin(UserPrincipal principal) {
        if (principal == null) {
            throw new FeesRuleException("INSUFFICIENT_PERMISSION", HttpStatus.FORBIDDEN,
                    "Authentication required");
        }
        Role role = principal.getRole();
        if (role != Role.SCHOOL_ADMIN && role != Role.ACCOUNTANT && role != Role.SUPER_ADMIN) {
            throw new FeesRuleException("INSUFFICIENT_PERMISSION", HttpStatus.FORBIDDEN,
                    "Only School Admin, Accountant, or Super Admin can perform this action");
        }
    }

    /**
     * Assert the caller is a parent with access to the given student.
     * The actual parent-student link check should be done by ParentAccessGuard.
     */
    public static void assertParent(UserPrincipal principal) {
        if (principal == null) {
            throw new FeesRuleException("INSUFFICIENT_PERMISSION", HttpStatus.FORBIDDEN,
                    "Authentication required");
        }
        if (principal.getRole() != Role.PARENT) {
            throw new FeesRuleException("INSUFFICIENT_PERMISSION", HttpStatus.FORBIDDEN,
                    "Only parents can perform this action");
        }
    }

    /**
     * Assert the caller is a student.
     */
    public static void assertStudent(UserPrincipal principal) {
        if (principal == null) {
            throw new FeesRuleException("INSUFFICIENT_PERMISSION", HttpStatus.FORBIDDEN,
                    "Authentication required");
        }
        if (principal.getRole() != Role.STUDENT) {
            throw new FeesRuleException("INSUFFICIENT_PERMISSION", HttpStatus.FORBIDDEN,
                    "Only students can perform this action");
        }
    }

    /**
     * Assert the caller has any role that can view fees (admin, accountant, parent, student).
     */
    public static void assertAnyFeeViewer(UserPrincipal principal) {
        if (principal == null) {
            throw new FeesRuleException("INSUFFICIENT_PERMISSION", HttpStatus.FORBIDDEN,
                    "Authentication required");
        }
        Role role = principal.getRole();
        if (role != Role.SCHOOL_ADMIN && role != Role.ACCOUNTANT
                && role != Role.PARENT && role != Role.STUDENT
                && role != Role.SUPER_ADMIN) {
            throw new FeesRuleException("INSUFFICIENT_PERMISSION", HttpStatus.FORBIDDEN,
                    "Insufficient permission to view fee data");
        }
    }

    /**
     * Assert the caller belongs to the given school (tenant boundary).
     * SUPER_ADMIN bypasses this check.
     */
    public static void assertSameSchool(UserPrincipal principal, Long schoolId) {
        if (principal == null) {
            throw new FeesRuleException("INSUFFICIENT_PERMISSION", HttpStatus.FORBIDDEN,
                    "Authentication required");
        }
        if (principal.getRole() == Role.SUPER_ADMIN) {
            return;
        }
        if (principal.getSchoolId() == null || !principal.getSchoolId().equals(schoolId)) {
            throw new FeesRuleException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "Resource not found");
        }
    }
}
