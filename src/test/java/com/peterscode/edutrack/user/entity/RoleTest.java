package com.peterscode.edutrack.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTest {

    @Test
    @DisplayName("SUPER_ADMIN can create SUPER_ADMIN and SCHOOL_ADMIN")
    void superAdminCanCreateSuperAdminAndSchoolAdmin() {
        assertThat(Role.creatableBy(Role.SUPER_ADMIN))
                .containsExactlyInAnyOrder(Role.SUPER_ADMIN, Role.SCHOOL_ADMIN);

        assertThat(Role.canCreate(Role.SUPER_ADMIN, Role.SUPER_ADMIN)).isTrue();
        assertThat(Role.canCreate(Role.SUPER_ADMIN, Role.SCHOOL_ADMIN)).isTrue();
        assertThat(Role.canCreate(Role.SUPER_ADMIN, Role.TEACHER)).isFalse();
        assertThat(Role.canCreate(Role.SUPER_ADMIN, Role.STUDENT)).isFalse();
        assertThat(Role.canCreate(Role.SUPER_ADMIN, Role.PARENT)).isFalse();
    }

    @Test
    @DisplayName("SCHOOL_ADMIN can create TEACHER, STUDENT, PARENT")
    void schoolAdminCanCreateSchoolUsers() {
        assertThat(Role.creatableBy(Role.SCHOOL_ADMIN))
                .containsExactlyInAnyOrder(Role.TEACHER, Role.STUDENT, Role.PARENT);

        assertThat(Role.canCreate(Role.SCHOOL_ADMIN, Role.SUPER_ADMIN)).isFalse();
        assertThat(Role.canCreate(Role.SCHOOL_ADMIN, Role.SCHOOL_ADMIN)).isFalse();
        assertThat(Role.canCreate(Role.SCHOOL_ADMIN, Role.TEACHER)).isTrue();
        assertThat(Role.canCreate(Role.SCHOOL_ADMIN, Role.STUDENT)).isTrue();
        assertThat(Role.canCreate(Role.SCHOOL_ADMIN, Role.PARENT)).isTrue();
    }

    @Test
    @DisplayName("TEACHER, STUDENT, PARENT cannot create any roles")
    void endUsersCannotCreateAnyRole() {
        assertThat(Role.creatableBy(Role.TEACHER)).isEmpty();
        assertThat(Role.creatableBy(Role.STUDENT)).isEmpty();
        assertThat(Role.creatableBy(Role.PARENT)).isEmpty();
    }
}
