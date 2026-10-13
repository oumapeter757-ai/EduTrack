package com.peterscode.edutrack.user.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * User roles with hierarchical creation permissions.
 * <p>
 * Registration hierarchy:
 * <ul>
 *   <li>Initial SUPER_ADMIN can sign up when no SUPER_ADMIN exists in the system (signup is disabled thereafter)</li>
 *   <li>SUPER_ADMIN → can create SUPER_ADMIN and SCHOOL_ADMIN (any school)</li>
 *   <li>SCHOOL_ADMIN → can create TEACHER, STUDENT, PARENT (own school only)</li>
 *   <li>TEACHER, STUDENT, PARENT → cannot create anyone</li>
 * </ul>
 */
public enum Role {

    SUPER_ADMIN,
    SCHOOL_ADMIN,
    TEACHER,
    STUDENT,
    PARENT;

    /**
     * Returns the set of roles that the given creator role is allowed to create.
     * Role is never client-submitted — determined by endpoint and caller's authority.
     */
    public static Set<Role> creatableBy(Role creatorRole) {
        return switch (creatorRole) {
            case SUPER_ADMIN -> EnumSet.of(SUPER_ADMIN, SCHOOL_ADMIN);
            case SCHOOL_ADMIN -> EnumSet.of(TEACHER, STUDENT, PARENT);
            case TEACHER, STUDENT, PARENT -> EnumSet.noneOf(Role.class);
        };
    }

    /**
     * Checks if the creator role can create the target role.
     */
    public static boolean canCreate(Role creatorRole, Role targetRole) {
        return creatableBy(creatorRole).contains(targetRole);
    }

    /**
     * Returns true if this role requires school-scoped creation
     * (creator must belong to the same school as the user being created).
     */
    public boolean requiresSameSchool() {
        return this == TEACHER || this == STUDENT || this == PARENT;
    }
}
