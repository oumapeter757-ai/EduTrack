package com.peterscode.edutrack.fees;

import com.peterscode.edutrack.fees.dto.LinkedChildView;
import com.peterscode.edutrack.fees.dto.FeeInvoiceView;
import com.peterscode.edutrack.fees.dto.FeePaymentView;
import com.peterscode.edutrack.fees.dto.FeeStatementView;
import com.peterscode.edutrack.fees.entity.StudentFeeAccount;
import com.peterscode.edutrack.fees.enums.FeeInvoiceStatus;
import com.peterscode.edutrack.fees.enums.FeePaymentChannel;
import com.peterscode.edutrack.fees.enums.FeePaymentStatus;
import com.peterscode.edutrack.fees.enums.LedgerEntryType;
import com.peterscode.edutrack.fees.enums.StudentFeeAccountStatus;
import com.peterscode.edutrack.fees.exception.FeesRuleException;
import com.peterscode.edutrack.fees.repository.FeeInvoiceRepository;
import com.peterscode.edutrack.fees.repository.FeePaymentRepository;
import com.peterscode.edutrack.fees.repository.LedgerEntryRepository;
import com.peterscode.edutrack.fees.repository.StudentFeeAccountRepository;
import com.peterscode.edutrack.fees.security.FeesAccessGuard;
import com.peterscode.edutrack.fees.service.FeeInvoiceService;
import com.peterscode.edutrack.fees.service.FeeStatementService;
import com.peterscode.edutrack.fees.service.PaymentPostingService;
import com.peterscode.edutrack.fees.service.LedgerService;
import com.peterscode.edutrack.guardian.entity.Guardian;
import com.peterscode.edutrack.guardian.entity.ParentStudentMapping;
import com.peterscode.edutrack.guardian.repository.GuardianRepository;
import com.peterscode.edutrack.guardian.repository.ParentStudentMappingRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.user.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Phase 3 unit tests: Parent and student views and permissions.
 */
@ExtendWith(MockitoExtension.class)
class FeesPhase3Test {

    // ==================== Test Data Builders ====================

    private UserPrincipal principal(Role role, Long schoolId) {
        return UserPrincipal.builder()
                .id(1L)
                .email("test@example.com")
                .role(role)
                .schoolId(schoolId)
                .build();
    }

    private Student buildStudent(Long schoolId, Long userId, Long id) {
        return Student.builder()
                .schoolId(schoolId)
                .userId(userId)
                .id(id)
                .admissionNumber("ADM001")
                .firstName("John")
                .lastName("Doe")
                .build();
    }

    private Guardian buildGuardian(Long schoolId, Long userId, Long id) {
        return Guardian.builder()
                .schoolId(schoolId)
                .userId(userId)
                .id(id)
                .firstName("Jane")
                .lastName("Doe")
                .build();
    }

    private ParentStudentMapping buildMapping(Long schoolId, Long parentId, Long studentId) {
        return ParentStudentMapping.builder()
                .schoolId(schoolId)
                .parentId(parentId)
                .studentId(studentId)
                .active(true)
                .build();
    }

    private StudentFeeAccount buildAccount(Long schoolId, Long studentId, BigDecimal balance) {
        return StudentFeeAccount.builder()
                .schoolId(schoolId)
                .studentId(studentId)
                .cachedBalance(balance)
                .currency("KES")
                .status(StudentFeeAccountStatus.ACTIVE)
                .build();
    }

    // ==================== Role Access Matrix ====================

    @Nested
    @DisplayName("Role access matrix — fees views")
    class RoleAccessMatrixTest {

        @Test
        @DisplayName("PARENT is allowed by assertAnyFeeViewer")
        void parentAllowedByAnyFeeViewer() {
            FeesAccessGuard.assertAnyFeeViewer(principal(Role.PARENT, 1L));
        }

        @Test
        @DisplayName("STUDENT is allowed by assertAnyFeeViewer")
        void studentAllowedByAnyFeeViewer() {
            FeesAccessGuard.assertAnyFeeViewer(principal(Role.STUDENT, 1L));
        }

        @Test
        @DisplayName("TEACHER is denied by assertAnyFeeViewer")
        void teacherDeniedByAnyFeeViewer() {
            assertThatThrownBy(() -> FeesAccessGuard.assertAnyFeeViewer(principal(Role.TEACHER, 1L)))
                    .isInstanceOf(FeesRuleException.class)
                    .extracting("errorCode").isEqualTo("INSUFFICIENT_PERMISSION");
        }

        @Test
        @DisplayName("LIBRARIAN is denied by assertAnyFeeViewer")
        void librarianDeniedByAnyFeeViewer() {
            assertThatThrownBy(() -> FeesAccessGuard.assertAnyFeeViewer(principal(Role.LIBRARIAN, 1L)))
                    .isInstanceOf(FeesRuleException.class)
                    .extracting("errorCode").isEqualTo("INSUFFICIENT_PERMISSION");
        }

        @Test
        @DisplayName("PARENT is denied by assertAdminOrAccountant")
        void parentDeniedByAdminOrAccountant() {
            assertThatThrownBy(() -> FeesAccessGuard.assertAdminOrAccountant(principal(Role.PARENT, 1L)))
                    .isInstanceOf(FeesRuleException.class)
                    .extracting("errorCode").isEqualTo("INSUFFICIENT_PERMISSION");
        }

        @Test
        @DisplayName("STUDENT is denied by assertAdminOrAccountant")
        void studentDeniedByAdminOrAccountant() {
            assertThatThrownBy(() -> FeesAccessGuard.assertAdminOrAccountant(principal(Role.STUDENT, 1L)))
                    .isInstanceOf(FeesRuleException.class)
                    .extracting("errorCode").isEqualTo("INSUFFICIENT_PERMISSION");
        }

        @Test
        @DisplayName("assertParent allows PARENT role")
        void assertParentAllowsParent() {
            FeesAccessGuard.assertParent(principal(Role.PARENT, 1L));
        }

        @Test
        @DisplayName("assertParent denies SCHOOL_ADMIN")
        void assertParentDeniesSchoolAdmin() {
            assertThatThrownBy(() -> FeesAccessGuard.assertParent(principal(Role.SCHOOL_ADMIN, 1L)))
                    .isInstanceOf(FeesRuleException.class)
                    .extracting("errorCode").isEqualTo("INSUFFICIENT_PERMISSION");
        }

        @Test
        @DisplayName("assertStudent allows STUDENT role")
        void assertStudentAllowsStudent() {
            FeesAccessGuard.assertStudent(principal(Role.STUDENT, 1L));
        }

        @Test
        @DisplayName("assertStudent denies PARENT")
        void assertStudentDeniesParent() {
            assertThatThrownBy(() -> FeesAccessGuard.assertStudent(principal(Role.PARENT, 1L)))
                    .isInstanceOf(FeesRuleException.class)
                    .extracting("errorCode").isEqualTo("INSUFFICIENT_PERMISSION");
        }
    }

    // ==================== Parent Access Guard ====================

    @Nested
    @DisplayName("ParentAccessGuard — parent-child link verification")
    class ParentAccessGuardTest {

        @Mock
        private GuardianRepository guardianRepository;
        @Mock
        private ParentStudentMappingRepository mappingRepository;

        @Test
        @DisplayName("Parent with active link can access child's fees")
        void parentWithActiveLinkCanAccess() {
            UserPrincipal parent = principal(Role.PARENT, 1L);
            Guardian guardian = buildGuardian(1L, 1L, 100L);
            ParentStudentMapping mapping = buildMapping(1L, 100L, 200L);

            when(guardianRepository.findBySchoolIdAndUserId(1L, 1L))
                    .thenReturn(Optional.of(guardian));
            when(mappingRepository.findBySchoolIdAndParentIdAndActiveTrue(1L, 100L))
                    .thenReturn(List.of(mapping));

            Long guardianId = com.peterscode.edutrack.security.ParentAccessGuard.assertLinked(
                    1L, parent, mappingRepository, guardianRepository, 200L);

            assertThat(guardianId).isEqualTo(100L);
        }

        @Test
        @DisplayName("Parent without link cannot access child's fees (404)")
        void parentWithoutLinkCannotAccess() {
            UserPrincipal parent = principal(Role.PARENT, 1L);
            Guardian guardian = buildGuardian(1L, 1L, 100L);

            when(guardianRepository.findBySchoolIdAndUserId(1L, 1L))
                    .thenReturn(Optional.of(guardian));
            when(mappingRepository.findBySchoolIdAndParentIdAndActiveTrue(1L, 100L))
                    .thenReturn(List.of());

            assertThatThrownBy(() ->
                    com.peterscode.edutrack.security.ParentAccessGuard.assertLinked(
                            1L, parent, mappingRepository, guardianRepository, 200L))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        @DisplayName("Parent with no guardian profile cannot access (404)")
        void parentWithNoGuardianProfileCannotAccess() {
            UserPrincipal parent = principal(Role.PARENT, 1L);

            when(guardianRepository.findBySchoolIdAndUserId(1L, 1L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    com.peterscode.edutrack.security.ParentAccessGuard.assertLinked(
                            1L, parent, mappingRepository, guardianRepository, 200L))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        @DisplayName("getLinkedStudentIds returns empty list for parent with no guardian")
        void getLinkedStudentIdsEmptyForNoGuardian() {
            UserPrincipal parent = principal(Role.PARENT, 1L);

            when(guardianRepository.findBySchoolIdAndUserId(1L, 1L))
                    .thenReturn(Optional.empty());

            List<Long> ids = com.peterscode.edutrack.security.ParentAccessGuard.getLinkedStudentIds(
                    1L, parent, mappingRepository, guardianRepository);

            assertThat(ids).isEmpty();
        }

        @Test
        @DisplayName("getLinkedStudentIds returns all linked student IDs")
        void getLinkedStudentIdsReturnsAll() {
            UserPrincipal parent = principal(Role.PARENT, 1L);
            Guardian guardian = buildGuardian(1L, 1L, 100L);
            ParentStudentMapping mapping1 = buildMapping(1L, 100L, 200L);
            ParentStudentMapping mapping2 = buildMapping(1L, 100L, 201L);

            when(guardianRepository.findBySchoolIdAndUserId(1L, 1L))
                    .thenReturn(Optional.of(guardian));
            when(mappingRepository.findBySchoolIdAndParentIdAndActiveTrue(1L, 100L))
                    .thenReturn(List.of(mapping1, mapping2));

            List<Long> ids = com.peterscode.edutrack.security.ParentAccessGuard.getLinkedStudentIds(
                    1L, parent, mappingRepository, guardianRepository);

            assertThat(ids).containsExactlyInAnyOrder(200L, 201L);
        }
    }

    // ==================== FeeInvoiceService — Student Self-Service ====================

    @Nested
    @DisplayName("FeeInvoiceService — student self-service")
    class FeeInvoiceServiceStudentTest {

        @Mock
        private FeeInvoiceRepository feeInvoiceRepository;
        @Mock
        private com.peterscode.edutrack.fees.repository.FeeStructureRepository feeStructureRepository;
        @Mock
        private StudentFeeAccountRepository studentFeeAccountRepository;
        @Mock
        private StudentRepository studentRepository;
        @Mock
        private com.peterscode.edutrack.timetable.repository.TermRepository termRepository;
        @Mock
        private LedgerService ledgerService;
        @Mock
        private GuardianRepository guardianRepository;
        @Mock
        private ParentStudentMappingRepository mappingRepository;

        private FeeInvoiceService service;

        @org.junit.jupiter.api.BeforeEach
        void setUp() {
            service = new FeeInvoiceService(
                    feeInvoiceRepository, feeStructureRepository,
                    studentFeeAccountRepository, studentRepository,
                    termRepository, ledgerService, guardianRepository, mappingRepository);
        }

        @Test
        @DisplayName("Student can list their own invoices")
        void studentCanListOwnInvoices() {
            UserPrincipal student = principal(Role.STUDENT, 1L);
            Student studentEntity = buildStudent(1L, 1L, 10L);

            when(studentRepository.findBySchoolIdAndUserId(1L, 1L))
                    .thenReturn(Optional.of(studentEntity));
            when(feeInvoiceRepository.findBySchoolIdAndStudentId(eq(1L), eq(10L), any(Pageable.class)))
                    .thenReturn(Page.empty());

            Page<FeeInvoiceView> result = service.listByStudent(student, null, Pageable.ofSize(20));

            assertThat(result).isEmpty();
            verify(studentRepository).findBySchoolIdAndUserId(eq(1L), eq(1L));
            verify(feeInvoiceRepository).findBySchoolIdAndStudentId(eq(1L), eq(10L), any(Pageable.class));
        }

        @Test
        @DisplayName("Student not found throws 404")
        void studentNotFoundThrows404() {
            UserPrincipal student = principal(Role.STUDENT, 1L);

            when(studentRepository.findBySchoolIdAndUserId(1L, 1L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.listByStudent(student, null, Pageable.ofSize(20)))
                    .isInstanceOf(FeesRuleException.class)
                    .extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");
        }
    }

    // ==================== PaymentPostingService — Student Self-Service ====================

    @Nested
    @DisplayName("PaymentPostingService — student self-service")
    class PaymentPostingServiceStudentTest {

        @Mock
        private FeePaymentRepository feePaymentRepository;
        @Mock
        private FeeInvoiceRepository feeInvoiceRepository;
        @Mock
        private com.peterscode.edutrack.fees.repository.PaymentAllocationRepository paymentAllocationRepository;
        @Mock
        private StudentFeeAccountRepository studentFeeAccountRepository;
        @Mock
        private StudentRepository studentRepository;
        @Mock
        private LedgerService ledgerService;
        @Mock
        private GuardianRepository guardianRepository;
        @Mock
        private ParentStudentMappingRepository mappingRepository;
        @Mock
        private java.time.Clock clock;

        private PaymentPostingService service;

        @org.junit.jupiter.api.BeforeEach
        void setUp() {
            service = new PaymentPostingService(
                    feePaymentRepository, feeInvoiceRepository,
                    paymentAllocationRepository, studentFeeAccountRepository,
                    studentRepository, guardianRepository, mappingRepository,
                    ledgerService, clock);
        }

        @Test
        @DisplayName("Student can list their own payments")
        void studentCanListOwnPayments() {
            UserPrincipal student = principal(Role.STUDENT, 1L);
            Student studentEntity = buildStudent(1L, 1L, 10L);

            when(studentRepository.findBySchoolIdAndUserId(1L, 1L))
                    .thenReturn(Optional.of(studentEntity));
            when(feePaymentRepository.findBySchoolIdAndStudentId(eq(1L), eq(10L), any(Pageable.class)))
                    .thenReturn(Page.empty());

            Page<FeePaymentView> result = service.listByStudent(student, null, Pageable.ofSize(20));

            assertThat(result).isEmpty();
            verify(studentRepository).findBySchoolIdAndUserId(eq(1L), eq(1L));
            verify(feePaymentRepository).findBySchoolIdAndStudentId(eq(1L), eq(10L), any(Pageable.class));
        }
    }

    // ==================== FeeStatementService — Parent Linked Children ====================

    @Nested
    @DisplayName("FeeStatementService — parent linked children")
    class FeeStatementServiceParentTest {

        @Mock
        private LedgerEntryRepository ledgerEntryRepository;
        @Mock
        private StudentFeeAccountRepository studentFeeAccountRepository;
        @Mock
        private StudentRepository studentRepository;
        @Mock
        private GuardianRepository guardianRepository;
        @Mock
        private ParentStudentMappingRepository mappingRepository;

        private FeeStatementService service;

        @org.junit.jupiter.api.BeforeEach
        void setUp() {
            service = new FeeStatementService(
                    ledgerEntryRepository, studentFeeAccountRepository, studentRepository,
                    guardianRepository, mappingRepository);
        }

        @Test
        @DisplayName("Parent can list linked children with balance")
        void parentCanListLinkedChildren() {
            UserPrincipal parent = principal(Role.PARENT, 1L);
            Guardian guardian = buildGuardian(1L, 1L, 100L);
            ParentStudentMapping mapping = buildMapping(1L, 100L, 200L);
            Student student = buildStudent(1L, 300L, 200L);
            StudentFeeAccount account = buildAccount(1L, 200L, new BigDecimal("5000.00"));

            when(guardianRepository.findBySchoolIdAndUserId(1L, 1L))
                    .thenReturn(Optional.of(guardian));
            when(mappingRepository.findBySchoolIdAndParentIdAndActiveTrue(1L, 100L))
                    .thenReturn(List.of(mapping));
            when(studentRepository.findBySchoolIdAndId(1L, 200L))
                    .thenReturn(Optional.of(student));
            when(studentFeeAccountRepository.findBySchoolIdAndStudentId(1L, 200L))
                    .thenReturn(Optional.of(account));

            List<LinkedChildView> children = service.listLinkedChildren(
                    parent, guardianRepository, mappingRepository);

            assertThat(children).hasSize(1);
            assertThat(children.get(0).studentId()).isEqualTo(200L);
            assertThat(children.get(0).studentName()).isEqualTo("John Doe");
            assertThat(children.get(0).admissionNumber()).isEqualTo("ADM001");
            assertThat(children.get(0).balance()).isEqualTo(new BigDecimal("5000.00"));
            assertThat(children.get(0).currency()).isEqualTo("KES");
        }

        @Test
        @DisplayName("Parent with no linked children returns empty list")
        void parentWithNoLinkedChildrenReturnsEmpty() {
            UserPrincipal parent = principal(Role.PARENT, 1L);
            Guardian guardian = buildGuardian(1L, 1L, 100L);

            when(guardianRepository.findBySchoolIdAndUserId(1L, 1L))
                    .thenReturn(Optional.of(guardian));
            when(mappingRepository.findBySchoolIdAndParentIdAndActiveTrue(1L, 100L))
                    .thenReturn(List.of());

            List<LinkedChildView> children = service.listLinkedChildren(
                    parent, guardianRepository, mappingRepository);

            assertThat(children).isEmpty();
        }

        @Test
        @DisplayName("Parent with child having no fee account shows zero balance")
        void parentWithChildNoFeeAccountShowsZero() {
            UserPrincipal parent = principal(Role.PARENT, 1L);
            Guardian guardian = buildGuardian(1L, 1L, 100L);
            ParentStudentMapping mapping = buildMapping(1L, 100L, 200L);
            Student student = buildStudent(1L, 300L, 200L);

            when(guardianRepository.findBySchoolIdAndUserId(1L, 1L))
                    .thenReturn(Optional.of(guardian));
            when(mappingRepository.findBySchoolIdAndParentIdAndActiveTrue(1L, 100L))
                    .thenReturn(List.of(mapping));
            when(studentRepository.findBySchoolIdAndId(1L, 200L))
                    .thenReturn(Optional.of(student));
            when(studentFeeAccountRepository.findBySchoolIdAndStudentId(1L, 200L))
                    .thenReturn(Optional.empty());

            List<LinkedChildView> children = service.listLinkedChildren(
                    parent, guardianRepository, mappingRepository);

            assertThat(children).hasSize(1);
            assertThat(children.get(0).balance()).isEqualTo(BigDecimal.ZERO);
            assertThat(children.get(0).currency()).isEqualTo("KES");
        }

        @Test
        @DisplayName("Non-parent role is denied from listLinkedChildren")
        void nonParentDeniedFromListLinkedChildren() {
            UserPrincipal teacher = principal(Role.TEACHER, 1L);

            assertThatThrownBy(() -> service.listLinkedChildren(
                    teacher, guardianRepository, mappingRepository))
                    .isInstanceOf(FeesRuleException.class)
                    .extracting("errorCode").isEqualTo("INSUFFICIENT_PERMISSION");
        }

        @Test
        @DisplayName("Parent with multiple children sees all")
        void parentWithMultipleChildrenSeesAll() {
            UserPrincipal parent = principal(Role.PARENT, 1L);
            Guardian guardian = buildGuardian(1L, 1L, 100L);
            ParentStudentMapping mapping1 = buildMapping(1L, 100L, 200L);
            ParentStudentMapping mapping2 = buildMapping(1L, 100L, 201L);
            Student student1 = buildStudent(1L, 300L, 200L);
            Student student2 = Student.builder()
                    .schoolId(1L).userId(301L).id(201L)
                    .admissionNumber("ADM002").firstName("Jane").lastName("Doe").build();
            StudentFeeAccount account1 = buildAccount(1L, 200L, new BigDecimal("3000.00"));
            StudentFeeAccount account2 = buildAccount(1L, 201L, new BigDecimal("7000.00"));

            when(guardianRepository.findBySchoolIdAndUserId(1L, 1L))
                    .thenReturn(Optional.of(guardian));
            when(mappingRepository.findBySchoolIdAndParentIdAndActiveTrue(1L, 100L))
                    .thenReturn(List.of(mapping1, mapping2));
            when(studentRepository.findBySchoolIdAndId(1L, 200L))
                    .thenReturn(Optional.of(student1));
            when(studentRepository.findBySchoolIdAndId(1L, 201L))
                    .thenReturn(Optional.of(student2));
            when(studentFeeAccountRepository.findBySchoolIdAndStudentId(1L, 200L))
                    .thenReturn(Optional.of(account1));
            when(studentFeeAccountRepository.findBySchoolIdAndStudentId(1L, 201L))
                    .thenReturn(Optional.of(account2));

            List<LinkedChildView> children = service.listLinkedChildren(
                    parent, guardianRepository, mappingRepository);

            assertThat(children).hasSize(2);
            assertThat(children).extracting("studentId")
                    .containsExactlyInAnyOrder(200L, 201L);
        }
    }

    // ==================== Cross-Tenant Isolation ====================

    @Nested
    @DisplayName("Cross-tenant isolation")
    class CrossTenantIsolationTest {

        @Test
        @DisplayName("assertSameSchool denies access to different school")
        void assertSameSchoolDeniesDifferentSchool() {
            UserPrincipal principal = principal(Role.SCHOOL_ADMIN, 1L);

            assertThatThrownBy(() -> FeesAccessGuard.assertSameSchool(principal, 2L))
                    .isInstanceOf(FeesRuleException.class)
                    .extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");
        }

        @Test
        @DisplayName("assertSameSchool allows access to same school")
        void assertSameSchoolAllowsSameSchool() {
            UserPrincipal principal = principal(Role.SCHOOL_ADMIN, 1L);

            FeesAccessGuard.assertSameSchool(principal, 1L);
        }

        @Test
        @DisplayName("SUPER_ADMIN bypasses same school check")
        void superAdminBypassesSameSchool() {
            UserPrincipal superAdmin = UserPrincipal.builder()
                    .id(99L).email("super@edu.com").role(Role.SUPER_ADMIN)
                    .schoolId(null).build();

            FeesAccessGuard.assertSameSchool(superAdmin, 1L);
            FeesAccessGuard.assertSameSchool(superAdmin, 999L);
        }
    }

    // ==================== LinkedChildView DTO ====================

    @Nested
    @DisplayName("LinkedChildView DTO")
    class LinkedChildViewTest {

        @Test
        @DisplayName("LinkedChildView contains all required fields")
        void linkedChildViewFields() {
            LinkedChildView view = new LinkedChildView(10L, "John Doe", "ADM001",
                    new BigDecimal("5000.00"), "KES");

            assertThat(view.studentId()).isEqualTo(10L);
            assertThat(view.studentName()).isEqualTo("John Doe");
            assertThat(view.admissionNumber()).isEqualTo("ADM001");
            assertThat(view.balance()).isEqualTo(new BigDecimal("5000.00"));
            assertThat(view.currency()).isEqualTo("KES");
        }
    }
}
