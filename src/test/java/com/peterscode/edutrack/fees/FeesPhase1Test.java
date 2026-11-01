package com.peterscode.edutrack.fees;

import com.peterscode.edutrack.fees.exception.FeesRuleException;
import com.peterscode.edutrack.fees.security.FeesAccessGuard;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.user.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 1 unit tests: ACCOUNTANT role, FeesAccessGuard, and FeesRuleException.
 */
class FeesPhase1Test {

    @Nested
    @DisplayName("Role enum — ACCOUNTANT")
    class RoleEnumTest {

        @Test
        @DisplayName("ACCOUNTANT exists in Role enum")
        void accountantExists() {
            assertThat(Role.ACCOUNTANT).isNotNull();
        }

        @Test
        @DisplayName("SCHOOL_ADMIN can create ACCOUNTANT")
        void schoolAdminCanCreateAccountant() {
            assertThat(Role.canCreate(Role.SCHOOL_ADMIN, Role.ACCOUNTANT)).isTrue();
        }

        @Test
        @DisplayName("ACCOUNTANT cannot create any role")
        void accountantCannotCreateAnyone() {
            assertThat(Role.canCreate(Role.ACCOUNTANT, Role.TEACHER)).isFalse();
            assertThat(Role.canCreate(Role.ACCOUNTANT, Role.STUDENT)).isFalse();
            assertThat(Role.canCreate(Role.ACCOUNTANT, Role.ACCOUNTANT)).isFalse();
        }

        @Test
        @DisplayName("ACCOUNTANT requires same school")
        void accountantRequiresSameSchool() {
            assertThat(Role.ACCOUNTANT.requiresSameSchool()).isTrue();
        }

        @Test
        @DisplayName("SUPER_ADMIN cannot create ACCOUNTANT directly")
        void superAdminCannotCreateAccountant() {
            assertThat(Role.canCreate(Role.SUPER_ADMIN, Role.ACCOUNTANT)).isFalse();
        }
    }

    @Nested
    @DisplayName("FeesAccessGuard — role assertions")
    class FeesAccessGuardTest {

        private UserPrincipal principal(Role role, Long schoolId) {
            return UserPrincipal.builder()
                    .id(1L)
                    .email("test@example.com")
                    .role(role)
                    .schoolId(schoolId)
                    .build();
        }

        @Test
        @DisplayName("assertAdminOrAccountant allows SCHOOL_ADMIN")
        void adminOrAccountantAllowsSchoolAdmin() {
            FeesAccessGuard.assertAdminOrAccountant(principal(Role.SCHOOL_ADMIN, 1L));
        }

        @Test
        @DisplayName("assertAdminOrAccountant allows ACCOUNTANT")
        void adminOrAccountantAllowsAccountant() {
            FeesAccessGuard.assertAdminOrAccountant(principal(Role.ACCOUNTANT, 1L));
        }

        @Test
        @DisplayName("assertAdminOrAccountant denies TEACHER")
        void adminOrAccountantDeniesTeacher() {
            assertThatThrownBy(() -> FeesAccessGuard.assertAdminOrAccountant(principal(Role.TEACHER, 1L)))
                    .isInstanceOf(FeesRuleException.class)
                    .extracting("errorCode").isEqualTo("INSUFFICIENT_PERMISSION");
        }

        @Test
        @DisplayName("assertAdminOrAccountant denies LIBRARIAN")
        void adminOrAccountantDeniesLibrarian() {
            assertThatThrownBy(() -> FeesAccessGuard.assertAdminOrAccountant(principal(Role.LIBRARIAN, 1L)))
                    .isInstanceOf(FeesRuleException.class)
                    .extracting("errorCode").isEqualTo("INSUFFICIENT_PERMISSION");
        }

        @Test
        @DisplayName("assertAdminOrAccountant denies PARENT")
        void adminOrAccountantDeniesParent() {
            assertThatThrownBy(() -> FeesAccessGuard.assertAdminOrAccountant(principal(Role.PARENT, 1L)))
                    .isInstanceOf(FeesRuleException.class)
                    .extracting("errorCode").isEqualTo("INSUFFICIENT_PERMISSION");
        }

        @Test
        @DisplayName("assertAdminOrAccountant denies STUDENT")
        void adminOrAccountantDeniesStudent() {
            assertThatThrownBy(() -> FeesAccessGuard.assertAdminOrAccountant(principal(Role.STUDENT, 1L)))
                    .isInstanceOf(FeesRuleException.class)
                    .extracting("errorCode").isEqualTo("INSUFFICIENT_PERMISSION");
        }

        @Test
        @DisplayName("assertAdminOrAccountant denies null principal")
        void adminOrAccountantDeniesNull() {
            assertThatThrownBy(() -> FeesAccessGuard.assertAdminOrAccountant(null))
                    .isInstanceOf(FeesRuleException.class)
                    .extracting("errorCode").isEqualTo("INSUFFICIENT_PERMISSION");
        }

        @Test
        @DisplayName("assertAdmin allows SCHOOL_ADMIN, ACCOUNTANT, SUPER_ADMIN")
        void adminAllowsAdminRoles() {
            FeesAccessGuard.assertAdmin(principal(Role.SCHOOL_ADMIN, 1L));
            FeesAccessGuard.assertAdmin(principal(Role.ACCOUNTANT, 1L));
            FeesAccessGuard.assertAdmin(principal(Role.SUPER_ADMIN, null));
        }

        @Test
        @DisplayName("assertAdmin denies TEACHER")
        void adminDeniesTeacher() {
            assertThatThrownBy(() -> FeesAccessGuard.assertAdmin(principal(Role.TEACHER, 1L)))
                    .isInstanceOf(FeesRuleException.class);
        }

        @Test
        @DisplayName("assertParent allows PARENT only")
        void parentAllowsParentOnly() {
            FeesAccessGuard.assertParent(principal(Role.PARENT, 1L));
            assertThatThrownBy(() -> FeesAccessGuard.assertParent(principal(Role.ACCOUNTANT, 1L)))
                    .isInstanceOf(FeesRuleException.class);
        }

        @Test
        @DisplayName("assertStudent allows STUDENT only")
        void studentAllowsStudentOnly() {
            FeesAccessGuard.assertStudent(principal(Role.STUDENT, 1L));
            assertThatThrownBy(() -> FeesAccessGuard.assertStudent(principal(Role.ACCOUNTANT, 1L)))
                    .isInstanceOf(FeesRuleException.class);
        }

        @Test
        @DisplayName("assertAnyFeeViewer allows SCHOOL_ADMIN, ACCOUNTANT, PARENT, STUDENT, SUPER_ADMIN")
        void anyFeeViewerAllowsFeeRoles() {
            FeesAccessGuard.assertAnyFeeViewer(principal(Role.SCHOOL_ADMIN, 1L));
            FeesAccessGuard.assertAnyFeeViewer(principal(Role.ACCOUNTANT, 1L));
            FeesAccessGuard.assertAnyFeeViewer(principal(Role.PARENT, 1L));
            FeesAccessGuard.assertAnyFeeViewer(principal(Role.STUDENT, 1L));
            FeesAccessGuard.assertAnyFeeViewer(principal(Role.SUPER_ADMIN, null));
        }

        @Test
        @DisplayName("assertAnyFeeViewer denies TEACHER and LIBRARIAN")
        void anyFeeViewerDeniesTeacherAndLibrarian() {
            assertThatThrownBy(() -> FeesAccessGuard.assertAnyFeeViewer(principal(Role.TEACHER, 1L)))
                    .isInstanceOf(FeesRuleException.class);
            assertThatThrownBy(() -> FeesAccessGuard.assertAnyFeeViewer(principal(Role.LIBRARIAN, 1L)))
                    .isInstanceOf(FeesRuleException.class);
        }

        @Test
        @DisplayName("assertSameSchool allows SUPER_ADMIN")
        void sameSchoolAllowsSuperAdmin() {
            FeesAccessGuard.assertSameSchool(principal(Role.SUPER_ADMIN, null), 99L);
        }

        @Test
        @DisplayName("assertSameSchool allows matching school")
        void sameSchoolAllowsMatchingSchool() {
            FeesAccessGuard.assertSameSchool(principal(Role.ACCOUNTANT, 1L), 1L);
        }

        @Test
        @DisplayName("assertSameSchool denies mismatched school")
        void sameSchoolDeniesMismatchedSchool() {
            assertThatThrownBy(() -> FeesAccessGuard.assertSameSchool(principal(Role.ACCOUNTANT, 1L), 99L))
                    .isInstanceOf(FeesRuleException.class)
                    .extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("FeesRuleException")
    class FeesRuleExceptionTest {

        @Test
        @DisplayName("Exception carries errorCode and status")
        void exceptionCarriesErrorCodeAndStatus() {
            FeesRuleException ex = new FeesRuleException(
                    "PAYMENT_ALREADY_PENDING", HttpStatus.CONFLICT, "Payment already pending");
            assertThat(ex.getErrorCode()).isEqualTo("PAYMENT_ALREADY_PENDING");
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(ex.getMessage()).isEqualTo("Payment already pending");
        }
    }
}
