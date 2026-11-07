package com.peterscode.edutrack.fees;

import com.peterscode.edutrack.fees.dto.CreateFeeStructureRequestDto;
import com.peterscode.edutrack.fees.dto.FeeInvoiceView;
import com.peterscode.edutrack.fees.dto.FeePaymentView;
import com.peterscode.edutrack.fees.dto.FeeStructureView;
import com.peterscode.edutrack.fees.dto.ManualPaymentRequestDto;
import com.peterscode.edutrack.fees.entity.*;
import com.peterscode.edutrack.fees.enums.*;
import com.peterscode.edutrack.fees.exception.FeesRuleException;
import com.peterscode.edutrack.fees.repository.*;
import com.peterscode.edutrack.fees.service.FeeInvoiceService;
import com.peterscode.edutrack.fees.service.FeeStructureService;
import com.peterscode.edutrack.fees.service.LedgerService;
import com.peterscode.edutrack.fees.service.PaymentPostingService;
import com.peterscode.edutrack.guardian.repository.GuardianRepository;
import com.peterscode.edutrack.guardian.repository.ParentStudentMappingRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.enums.StudentStatus;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.timetable.entity.Term;
import com.peterscode.edutrack.timetable.repository.TermRepository;
import com.peterscode.edutrack.user.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 2 unit tests: fee structures, invoices, ledger, payments.
 */
@ExtendWith(MockitoExtension.class)
class FeesPhase2Test {

    // ==================== Fixtures ====================

    UserPrincipal schoolAdmin() {
        return UserPrincipal.builder().id(1L).email("admin@school.com")
                .role(Role.SCHOOL_ADMIN).schoolId(10L).build();
    }

    UserPrincipal accountant() {
        return UserPrincipal.builder().id(2L).email("acct@school.com")
                .role(Role.ACCOUNTANT).schoolId(10L).build();
    }

    UserPrincipal teacher() {
        return UserPrincipal.builder().id(3L).email("teacher@school.com")
                .role(Role.TEACHER).schoolId(10L).build();
    }

    Term term() {
        return Term.builder().id(1L).schoolId(10L).academicYearId(1L)
                .name("Term 1").startDate(LocalDate.of(2025, 1, 6))
                .endDate(LocalDate.of(2025, 5, 10)).build();
    }

    Student student() {
        return Student.builder().id(100L).schoolId(10L).admissionNumber("ADM001")
                .firstName("John").lastName("Doe").status(StudentStatus.ACTIVE)
                .classLevelId(5L).build();
    }

    StudentFeeAccount account() {
        return StudentFeeAccount.builder().id(1L).schoolId(10L).studentId(100L)
                .currency("KES").cachedBalance(BigDecimal.ZERO).build();
    }

    // ==================== Fee Structure Tests ====================

    @Nested
    @DisplayName("FeeStructureService")
    class FeeStructureServiceTest {

        @Mock FeeStructureRepository feeStructureRepository;
        @Mock TermRepository termRepository;
        @InjectMocks FeeStructureService service;

        @Test
        @DisplayName("Create fee structure succeeds")
        void createFeeStructure() {
            when(termRepository.findBySchoolIdAndId(10L, 1L)).thenReturn(Optional.of(term()));
            when(feeStructureRepository.existsBySchoolIdAndTermIdAndClassLevelIdAndName(
                    10L, 1L, 5L, "Tuition")).thenReturn(false);
            when(feeStructureRepository.save(any(FeeStructure.class))).thenAnswer(i -> {
                FeeStructure s = i.getArgument(0);
                s.setId(1L);
                return s;
            });

            CreateFeeStructureRequestDto request = new CreateFeeStructureRequestDto(
                    1L, 5L, "Tuition",
                    new CreateFeeStructureRequestDto.CreateFeeItemRequestDto[]{
                            new CreateFeeStructureRequestDto.CreateFeeItemRequestDto("Tuition Fee", new BigDecimal("5000"), true)
                    });

            FeeStructureView view = service.create(schoolAdmin(), request);

            assertThat(view).isNotNull();
            assertThat(view.name()).isEqualTo("Tuition");
            assertThat(view.items()).hasSize(1);
        }

        @Test
        @DisplayName("Create fee structure denied for teacher")
        void createFeeStructureDeniedForTeacher() {
            assertThatThrownBy(() -> service.create(teacher(),
                    new CreateFeeStructureRequestDto(1L, 5L, "Tuition",
                            new CreateFeeStructureRequestDto.CreateFeeItemRequestDto[0])))
                    .isInstanceOf(FeesRuleException.class)
                    .extracting("errorCode").isEqualTo("INSUFFICIENT_PERMISSION");
        }

        @Test
        @DisplayName("Duplicate fee structure returns CONFLICT")
        void duplicateFeeStructure() {
            when(feeStructureRepository.existsBySchoolIdAndTermIdAndClassLevelIdAndName(
                    10L, 1L, 5L, "Tuition")).thenReturn(true);

            CreateFeeStructureRequestDto request = new CreateFeeStructureRequestDto(
                    1L, 5L, "Tuition",
                    new CreateFeeStructureRequestDto.CreateFeeItemRequestDto[0]);

            assertThatThrownBy(() -> service.create(schoolAdmin(), request))
                    .isInstanceOf(FeesRuleException.class)
                    .extracting("errorCode").isEqualTo("DUPLICATE_FEE_STRUCTURE");
        }
    }

    // ==================== Invoice Tests ====================

    @Nested
    @DisplayName("FeeInvoiceService")
    class FeeInvoiceServiceTest {

        @Mock FeeInvoiceRepository feeInvoiceRepository;
        @Mock FeeStructureRepository feeStructureRepository;
        @Mock StudentFeeAccountRepository studentFeeAccountRepository;
        @Mock StudentRepository studentRepository;
        @Mock TermRepository termRepository;
        @Mock LedgerService ledgerService;
        @InjectMocks FeeInvoiceService service;

        @Test
        @DisplayName("Generate invoices creates invoices for active students")
        void generateInvoices() {
            FeeStructure structure = FeeStructure.builder().id(1L).schoolId(10L)
                    .termId(1L).classLevelId(5L).name("Tuition")
                    .items(List.of(FeeItem.builder().id(1L).structureId(1L)
                            .name("Tuition Fee").amount(new BigDecimal("5000")).build()))
                    .build();

            when(feeStructureRepository.findBySchoolIdAndId(10L, 1L)).thenReturn(Optional.of(structure));
            when(studentRepository.findBySchoolIdAndClassLevelIdAndStatus(
                    10L, 5L, StudentStatus.ACTIVE)).thenReturn(List.of(student()));
            when(feeInvoiceRepository.existsBySchoolIdAndStudentIdAndTermIdAndStructureId(
                    10L, 100L, 1L, 1L)).thenReturn(false);
            when(studentFeeAccountRepository.findBySchoolIdAndStudentId(10L, 100L))
                    .thenReturn(Optional.of(account()));
            when(termRepository.findBySchoolIdAndId(10L, 1L)).thenReturn(Optional.of(term()));
            when(feeInvoiceRepository.save(any(FeeInvoice.class))).thenAnswer(i -> {
                FeeInvoice inv = i.getArgument(0);
                inv.setId(1L);
                return inv;
            });

            int count = service.generateInvoices(schoolAdmin(), 1L);

            assertThat(count).isEqualTo(1);
            verify(feeInvoiceRepository).save(any(FeeInvoice.class));
            verify(ledgerService).postCharge(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Generate invoices is idempotent")
        void generateInvoicesIdempotent() {
            FeeStructure structure = FeeStructure.builder().id(1L).schoolId(10L)
                    .termId(1L).classLevelId(5L).name("Tuition").items(List.of()).build();

            when(feeStructureRepository.findBySchoolIdAndId(10L, 1L)).thenReturn(Optional.of(structure));
            when(studentRepository.findBySchoolIdAndClassLevelIdAndStatus(
                    10L, 5L, StudentStatus.ACTIVE)).thenReturn(List.of(student()));
            when(feeInvoiceRepository.existsBySchoolIdAndStudentIdAndTermIdAndStructureId(
                    10L, 100L, 1L, 1L)).thenReturn(true);

            int count = service.generateInvoices(schoolAdmin(), 1L);

            assertThat(count).isEqualTo(0);
            verify(feeInvoiceRepository, never()).save(any());
        }
    }

    // ==================== Ledger Tests ====================

    @Nested
    @DisplayName("LedgerService")
    class LedgerServiceTest {

        @Mock LedgerEntryRepository ledgerEntryRepository;

        LedgerService service() {
            return new LedgerService(ledgerEntryRepository, Clock.systemUTC());
        }

        @Test
        @DisplayName("Post charge creates positive entry")
        void postCharge() {
            StudentFeeAccount account = account();
            FeeInvoice invoice = FeeInvoice.builder().id(1L).build();

            when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(i -> i.getArgument(0));

            LedgerEntry entry = service().postCharge(account, invoice, "Tuition", new BigDecimal("5000"));

            assertThat(entry.getType()).isEqualTo(LedgerEntryType.CHARGE);
            assertThat(entry.getAmount()).isEqualTo(new BigDecimal("5000"));
            assertThat(entry.getInvoiceId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Post payment creates negative entry")
        void postPayment() {
            StudentFeeAccount account = account();

            when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(i -> i.getArgument(0));

            LedgerEntry entry = service().postPayment(account, 1L, 1L, "Cash payment", new BigDecimal("3000"));

            assertThat(entry.getType()).isEqualTo(LedgerEntryType.PAYMENT);
            assertThat(entry.getAmount()).isEqualTo(new BigDecimal("-3000"));
        }
    }

    // ==================== Payment Posting Tests ====================

    @Nested
    @DisplayName("PaymentPostingService")
    class PaymentPostingServiceTest {

        @Mock FeePaymentRepository feePaymentRepository;
        @Mock FeeInvoiceRepository feeInvoiceRepository;
        @Mock PaymentAllocationRepository paymentAllocationRepository;
        @Mock StudentFeeAccountRepository studentFeeAccountRepository;
        @Mock StudentRepository studentRepository;
        @Mock GuardianRepository guardianRepository;
        @Mock ParentStudentMappingRepository mappingRepository;
        @Mock LedgerService ledgerService;

        PaymentPostingService service() {
            return new PaymentPostingService(
                    feePaymentRepository, feeInvoiceRepository, paymentAllocationRepository,
                    studentFeeAccountRepository, studentRepository, guardianRepository,
                    mappingRepository, ledgerService, Clock.systemUTC());
        }

        @Test
        @DisplayName("Manual payment records and allocates")
        void recordManualPayment() {
            Student student = student();
            StudentFeeAccount account = account();
            when(studentRepository.findBySchoolIdAndId(10L, 100L)).thenReturn(Optional.of(student));
            when(studentFeeAccountRepository.findBySchoolIdAndStudentIdForUpdate(10L, 100L))
                    .thenReturn(Optional.of(account));
            when(feePaymentRepository.save(any(FeePayment.class))).thenAnswer(i -> {
                FeePayment p = i.getArgument(0);
                p.setId(1L);
                return p;
            });
            when(feeInvoiceRepository.findBySchoolIdAndStudentIdAndStatusInOrderByIssuedOnAscForUpdate(
                    anyLong(), anyLong(), anyList())).thenReturn(List.of());

            ManualPaymentRequestDto request = new ManualPaymentRequestDto(
                    100L, new BigDecimal("5000"), FeePaymentChannel.MANUAL,
                    "254712345678", "REF001");

            FeePaymentView view = service().recordManualPayment(schoolAdmin(), request);

            assertThat(view).isNotNull();
            assertThat(view.status()).isEqualTo(FeePaymentStatus.SUCCEEDED);
            assertThat(view.payerPhoneMasked()).isEqualTo("********5678");
            assertThat(view.receiptNumber()).isNotNull();
        }

        @Test
        @DisplayName("Manual payment denied for teacher")
        void manualPaymentDeniedForTeacher() {
            ManualPaymentRequestDto request = new ManualPaymentRequestDto(
                    100L, new BigDecimal("5000"), FeePaymentChannel.MANUAL, null, null);

            assertThatThrownBy(() -> service().recordManualPayment(teacher(), request))
                    .isInstanceOf(FeesRuleException.class)
                    .extracting("errorCode").isEqualTo("INSUFFICIENT_PERMISSION");
        }

        @Test
        @DisplayName("Allocate payment to open invoices oldest first")
        void allocatePaymentToInvoices() {
            StudentFeeAccount account = account();
            FeePayment payment = FeePayment.builder().id(1L).schoolId(10L)
                    .studentId(100L).amount(new BigDecimal("8000")).build();

            FeeInvoice invoice1 = FeeInvoice.builder().id(1L).schoolId(10L).studentId(100L)
                    .total(new BigDecimal("5000")).paid(BigDecimal.ZERO)
                    .status(FeeInvoiceStatus.OPEN).build();
            FeeInvoice invoice2 = FeeInvoice.builder().id(2L).schoolId(10L).studentId(100L)
                    .total(new BigDecimal("4000")).paid(BigDecimal.ZERO)
                    .status(FeeInvoiceStatus.OPEN).build();

            // The deprecated overload delegates to the new one, which locks the account
            when(studentFeeAccountRepository.findBySchoolIdAndStudentIdForUpdate(10L, 100L))
                    .thenReturn(Optional.of(account));
            when(feeInvoiceRepository.findBySchoolIdAndStudentIdAndStatusInOrderByIssuedOnAscForUpdate(
                    anyLong(), anyLong(), anyList())).thenReturn(List.of(invoice1, invoice2));
            when(feeInvoiceRepository.save(any(FeeInvoice.class))).thenAnswer(i -> i.getArgument(0));
            when(paymentAllocationRepository.saveAll(anyList())).thenReturn(List.of());

            service().allocatePaymentToInvoices(account, payment);

            assertThat(invoice1.getStatus()).isEqualTo(FeeInvoiceStatus.PAID);
            assertThat(invoice1.getPaid()).isEqualTo(new BigDecimal("5000"));
            assertThat(invoice2.getStatus()).isEqualTo(FeeInvoiceStatus.PARTLY_PAID);
            assertThat(invoice2.getPaid()).isEqualTo(new BigDecimal("3000"));
        }
    }

    // ==================== Enum Tests ====================

    @Nested
    @DisplayName("Fees enums")
    class FeesEnumsTest {

        @Test
        @DisplayName("FeeInvoiceStatus has all expected values")
        void feeInvoiceStatusValues() {
            assertThat(FeeInvoiceStatus.values()).containsExactly(
                    FeeInvoiceStatus.OPEN, FeeInvoiceStatus.PARTLY_PAID,
                    FeeInvoiceStatus.PAID, FeeInvoiceStatus.CANCELLED);
        }

        @Test
        @DisplayName("LedgerEntryType has all expected values")
        void ledgerEntryTypeValues() {
            assertThat(LedgerEntryType.values()).containsExactly(
                    LedgerEntryType.CHARGE, LedgerEntryType.PAYMENT,
                    LedgerEntryType.DISCOUNT, LedgerEntryType.WAIVER,
                    LedgerEntryType.ADJUSTMENT, LedgerEntryType.REVERSAL);
        }

        @Test
        @DisplayName("FeePaymentStatus has all expected values")
        void feePaymentStatusValues() {
            assertThat(FeePaymentStatus.values()).containsExactly(
                    FeePaymentStatus.PENDING, FeePaymentStatus.SUCCEEDED,
                    FeePaymentStatus.FAILED, FeePaymentStatus.TIMED_OUT,
                    FeePaymentStatus.UNALLOCATED, FeePaymentStatus.HELD,
                    FeePaymentStatus.REVERSED);
        }

        @Test
        @DisplayName("FeePaymentChannel has all expected values")
        void feePaymentChannelValues() {
            assertThat(FeePaymentChannel.values()).containsExactly(
                    FeePaymentChannel.STK, FeePaymentChannel.PAYBILL,
                    FeePaymentChannel.MANUAL);
        }
    }
}
