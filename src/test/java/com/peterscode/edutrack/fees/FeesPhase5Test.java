package com.peterscode.edutrack.fees;

import com.peterscode.edutrack.fees.dto.StkPushRequestDto;
import com.peterscode.edutrack.fees.dto.StkPushResponseDto;
import com.peterscode.edutrack.fees.entity.*;
import com.peterscode.edutrack.fees.enums.*;
import com.peterscode.edutrack.fees.exception.FeesRuleException;
import com.peterscode.edutrack.fees.mpesa.FakeMpesaClient;
import com.peterscode.edutrack.fees.mpesa.MpesaClient;
import com.peterscode.edutrack.fees.repository.*;
import com.peterscode.edutrack.fees.service.*;
import com.peterscode.edutrack.fees.security.FeesAccessGuard;
import com.peterscode.edutrack.guardian.entity.Guardian;
import com.peterscode.edutrack.guardian.repository.GuardianRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.enums.StudentStatus;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.user.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 5 unit tests: STK Push initiation, callback processing, idempotency,
 * timeout job, and status queries.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeesPhase5Test {

    private UserPrincipal principal(Role role, Long schoolId) {
        return UserPrincipal.builder()
                .id(1L)
                .email("test@example.com")
                .role(role)
                .schoolId(schoolId)
                .build();
    }

    private Student buildStudent(Long schoolId, Long id, String admissionNumber) {
        return Student.builder()
                .id(id)
                .schoolId(schoolId)
                .admissionNumber(admissionNumber)
                .firstName("Test")
                .lastName("Student")
                .status(StudentStatus.ACTIVE)
                .build();
    }

    private Guardian buildGuardian(Long schoolId, Long userId, String phone) {
        return Guardian.builder()
                .id(1L)
                .schoolId(schoolId)
                .userId(userId)
                .phone(phone)
                .build();
    }

    // ==================== MpesaClient Interface ====================

    @Nested
    @DisplayName("MpesaClient — interface and FakeMpesaClient")
    class MpesaClientTest {

        @Test
        @DisplayName("FakeMpesaClient implements MpesaClient")
        void fakeImplementsInterface() {
            MpesaClient client = new FakeMpesaClient();
            assertThat(client).isInstanceOf(MpesaClient.class);
        }

        @Test
        @DisplayName("FakeMpesaClient initiates STK Push successfully")
        void initiateStkPushReturnsSuccess() {
            FakeMpesaClient client = new FakeMpesaClient();
            MpesaClient.StkPushResult result = client.initiateStkPush(
                    "254712345678", new BigDecimal("1000"),
                    "https://example.com/callback", "REF-001", "Test");
            assertThat(result.success()).isTrue();
            assertThat(result.checkoutRequestID()).isNotBlank();
            assertThat(result.customerMessage()).isNotBlank();
        }

        @Test
        @DisplayName("FakeMpesaClient status query returns configured outcome")
        void queryStatusReturnsConfiguredOutcome() {
            FakeMpesaClient client = new FakeMpesaClient();
            MpesaClient.StkPushResult init = client.initiateStkPush(
                    "254712345678", new BigDecimal("1000"),
                    "https://example.com/callback", "REF-001", "Test");
            client.setOutcome(init.checkoutRequestID(),
                    FakeMpesaClient.SimulatedOutcome.FAILED);
            MpesaClient.StkPushStatusResult status = client.queryStkPushStatus(
                    init.checkoutRequestID());
            assertThat(status.resultCode()).isEqualTo("1");
            assertThat(status.completed()).isTrue();
        }

        @Test
        @DisplayName("FakeMpesaClient default outcome is SUCCESS")
        void defaultOutcomeIsSuccess() {
            FakeMpesaClient client = new FakeMpesaClient();
            MpesaClient.StkPushResult init = client.initiateStkPush(
                    "254712345678", new BigDecimal("1000"),
                    "https://example.com/callback", "REF-001", "Test");
            MpesaClient.StkPushStatusResult status = client.queryStkPushStatus(
                    init.checkoutRequestID());
            assertThat(status.resultCode()).isEqualTo("0");
        }

        @Test
        @DisplayName("SimulatedOutcome enum has all expected values")
        void simulatedOutcomeEnumValues() {
            FakeMpesaClient.SimulatedOutcome[] values = FakeMpesaClient.SimulatedOutcome.values();
            assertThat(values).hasSize(4);
            assertThat(values).extracting(Enum::name)
                    .containsExactly("SUCCESS", "FAILED", "CANCELLED", "TIMEOUT");
        }
    }

    // ==================== STK Push Initiation ====================

    @Nested
    @DisplayName("StkPushService — STK Push initiation")
    class StkPushInitiationTest {

        @Mock
        private FeePaymentRepository feePaymentRepository;
        @Mock
        private StudentRepository studentRepository;
        @Mock
        private GuardianRepository guardianRepository;
        @Mock
        private StudentFeeAccountRepository studentFeeAccountRepository;

        private FakeMpesaClient mpesaClient;
        private StkPushService service;

        @org.junit.jupiter.api.BeforeEach
        void setUp() {
            mpesaClient = new FakeMpesaClient();
            Clock clock = Clock.fixed(Instant.parse("2025-01-01T12:00:00Z"), ZoneOffset.UTC);
            service = new StkPushService(
                    mpesaClient, feePaymentRepository, studentRepository,
                    studentFeeAccountRepository, guardianRepository, clock);
        }

        @Test
        @DisplayName("Parent initiates STK Push successfully")
        void parentInitiatesStkPush() {
            UserPrincipal principal = principal(Role.PARENT, 1L);
            StkPushRequestDto request = new StkPushRequestDto(1L, new BigDecimal("5000"), "254712345678");

            Student student = buildStudent(1L, 1L, "STU001");
            when(studentRepository.findBySchoolIdAndId(1L, 1L)).thenReturn(Optional.of(student));
            when(feePaymentRepository.findBySchoolIdAndStudentIdAndStatus(
                    1L, 1L, FeePaymentStatus.PENDING)).thenReturn(Optional.empty());

            FeePayment saved = FeePayment.builder()
                    .id(1L).schoolId(1L).studentId(1L)
                    .channel(FeePaymentChannel.STK).amount(new BigDecimal("5000"))
                    .currency("KES").status(FeePaymentStatus.PENDING)
                    .initiatedBy(1L).createdAt(LocalDateTime.now())
                    .build();
            when(feePaymentRepository.save(any(FeePayment.class))).thenReturn(saved);
            when(feePaymentRepository.findById(1L)).thenReturn(Optional.of(saved));

            StkPushResponseDto response = service.initiate(principal, request);
            assertThat(response).isNotNull();
            assertThat(response.paymentId()).isEqualTo(1L);
            assertThat(response.status()).isEqualTo(FeePaymentStatus.PENDING);
            assertThat(response.amount()).isEqualTo(new BigDecimal("5000"));
        }

        @Test
        @DisplayName("Student initiates STK Push for self")
        void studentInitiatesStkPushForSelf() {
            UserPrincipal principal = principal(Role.STUDENT, 1L);
            StkPushRequestDto request = new StkPushRequestDto(null, new BigDecimal("2000"), "254712345678");

            Student student = buildStudent(1L, 1L, "STU001");
            student.setUserId(1L);
            when(studentRepository.findBySchoolIdAndUserId(1L, 1L)).thenReturn(Optional.of(student));
            when(studentRepository.findBySchoolIdAndId(1L, 1L)).thenReturn(Optional.of(student));
            when(feePaymentRepository.findBySchoolIdAndStudentIdAndStatus(
                    1L, 1L, FeePaymentStatus.PENDING)).thenReturn(Optional.empty());

            FeePayment saved = FeePayment.builder()
                    .id(1L).schoolId(1L).studentId(1L)
                    .channel(FeePaymentChannel.STK).amount(new BigDecimal("2000"))
                    .currency("KES").status(FeePaymentStatus.PENDING)
                    .initiatedBy(1L).createdAt(LocalDateTime.now())
                    .build();
            when(feePaymentRepository.save(any(FeePayment.class))).thenReturn(saved);
            when(feePaymentRepository.findById(1L)).thenReturn(Optional.of(saved));

            StkPushResponseDto response = service.initiate(principal, request);
            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(FeePaymentStatus.PENDING);
        }

        @Test
        @DisplayName("Duplicate pending payment returns 409")
        void duplicatePendingPaymentReturnsConflict() {
            UserPrincipal principal = principal(Role.PARENT, 1L);
            StkPushRequestDto request = new StkPushRequestDto(1L, new BigDecimal("5000"), "254712345678");

            Student student = buildStudent(1L, 1L, "STU001");
            when(studentRepository.findBySchoolIdAndId(1L, 1L)).thenReturn(Optional.of(student));
            when(feePaymentRepository.findBySchoolIdAndStudentIdAndStatus(
                    1L, 1L, FeePaymentStatus.PENDING)).thenReturn(Optional.of(FeePayment.builder().build()));

            assertThatThrownBy(() -> service.initiate(principal, request))
                    .isInstanceOf(FeesRuleException.class)
                    .hasMessageContaining("pending");
        }

        @Test
        @DisplayName("Invalid phone number is rejected")
        void invalidPhoneRejected() {
            UserPrincipal principal = principal(Role.PARENT, 1L);
            StkPushRequestDto request = new StkPushRequestDto(1L, new BigDecimal("5000"), "0712345678");

            Student student = buildStudent(1L, 1L, "STU001");
            when(studentRepository.findBySchoolIdAndId(1L, 1L)).thenReturn(Optional.of(student));

            assertThatThrownBy(() -> service.initiate(principal, request))
                    .isInstanceOf(FeesRuleException.class)
                    .hasMessageContaining("Phone");
        }

        @Test
        @DisplayName("Zero amount is rejected")
        void zeroAmountRejected() {
            UserPrincipal principal = principal(Role.PARENT, 1L);
            StkPushRequestDto request = new StkPushRequestDto(1L, BigDecimal.ZERO, "254712345678");

            Student student = buildStudent(1L, 1L, "STU001");
            when(studentRepository.findBySchoolIdAndId(1L, 1L)).thenReturn(Optional.of(student));

            assertThatThrownBy(() -> service.initiate(principal, request))
                    .isInstanceOf(FeesRuleException.class)
                    .hasMessageContaining("Amount");
        }

        @Test
        @DisplayName("Non-parent/student role is rejected")
        void nonParentStudentRoleRejected() {
            UserPrincipal principal = principal(Role.TEACHER, 1L);
            StkPushRequestDto request = new StkPushRequestDto(1L, new BigDecimal("5000"), "254712345678");

            assertThatThrownBy(() -> service.initiate(principal, request))
                    .isInstanceOf(FeesRuleException.class);
        }

        @Test
        @DisplayName("Student not found returns 404")
        void studentNotFoundReturns404() {
            UserPrincipal principal = principal(Role.PARENT, 1L);
            StkPushRequestDto request = new StkPushRequestDto(999L, new BigDecimal("5000"), "254712345678");

            when(studentRepository.findBySchoolIdAndId(1L, 999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.initiate(principal, request))
                    .isInstanceOf(FeesRuleException.class)
                    .hasMessageContaining("not found");
        }
    }

    // ==================== STK Callback Processing ====================

    @Nested
    @DisplayName("StkCallbackService — STK callback processing")
    class StkCallbackTest {

        @Mock
        private CallbackInboxRepository inboxRepository;
        @Mock
        private FeePaymentRepository feePaymentRepository;
        @Mock
        private StudentFeeAccountRepository studentFeeAccountRepository;
        @Mock
        private PaymentPostingService paymentPostingService;
        @Mock
        private LedgerService ledgerService;
        @Mock
        private SchoolPaymentConfigService configService;

        private StkCallbackService service;

        @org.junit.jupiter.api.BeforeEach
        void setUp() {
            service = new StkCallbackService(
                    inboxRepository, feePaymentRepository, studentFeeAccountRepository,
                    paymentPostingService, ledgerService, configService);
        }

        @Test
        @DisplayName("Successful STK callback posts payment")
        void successfulCallbackPostsPayment() {
            FeePayment payment = FeePayment.builder()
                    .id(1L).schoolId(1L).studentId(1L)
                    .channel(FeePaymentChannel.STK).amount(new BigDecimal("1000"))
                    .status(FeePaymentStatus.PENDING)
                    .checkoutRequestID("CHK-001")
                    .build();
            when(feePaymentRepository.findBySchoolIdAndCheckoutRequestID(
                    1L, "CHK-001")).thenReturn(Optional.of(payment));
            when(feePaymentRepository.save(any(FeePayment.class))).thenReturn(payment);

            StudentFeeAccount account = StudentFeeAccount.builder()
                    .id(1L).schoolId(1L).studentId(1L).build();
            when(studentFeeAccountRepository.findBySchoolIdAndStudentId(1L, 1L))
                    .thenReturn(Optional.of(account));
            when(inboxRepository.save(any(CallbackInbox.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.processStkResult(1L, "CHK-001", 0, "Success",
                    new BigDecimal("1000"), "254712345678", "RCP001", Instant.now());

            assertThat(payment.getStatus()).isEqualTo(FeePaymentStatus.SUCCEEDED);
            assertThat(payment.getProviderReceipt()).isEqualTo("RCP001");
            verify(paymentPostingService).allocatePaymentToInvoices(any(), any());
        }

        @Test
        @DisplayName("Failed STK callback marks payment as FAILED")
        void failedCallbackMarksFailed() {
            FeePayment payment = FeePayment.builder()
                    .id(1L).schoolId(1L).studentId(1L)
                    .channel(FeePaymentChannel.STK).amount(new BigDecimal("1000"))
                    .status(FeePaymentStatus.PENDING)
                    .checkoutRequestID("CHK-001")
                    .build();
            when(feePaymentRepository.findBySchoolIdAndCheckoutRequestID(
                    1L, "CHK-001")).thenReturn(Optional.of(payment));
            when(feePaymentRepository.save(any(FeePayment.class))).thenReturn(payment);
            when(inboxRepository.save(any(CallbackInbox.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.processStkResult(1L, "CHK-001", 1, "Cancelled by user",
                    new BigDecimal("1000"), "254712345678", null, Instant.now());

            assertThat(payment.getStatus()).isEqualTo(FeePaymentStatus.FAILED);
            assertThat(payment.getFailureReason()).isEqualTo("Cancelled by user");
        }

        @Test
        @DisplayName("Idempotent callback skips already processed payment")
        void idempotentCallbackSkips() {
            FeePayment payment = FeePayment.builder()
                    .id(1L).schoolId(1L).studentId(1L)
                    .status(FeePaymentStatus.SUCCEEDED)
                    .checkoutRequestID("CHK-001")
                    .build();
            when(feePaymentRepository.findBySchoolIdAndCheckoutRequestID(
                    1L, "CHK-001")).thenReturn(Optional.of(payment));
            when(inboxRepository.save(any(CallbackInbox.class)))
                    .thenAnswer(inv -> {
                        CallbackInbox inbox = inv.getArgument(0);
                        inbox.setProcessed(true);
                        return inbox;
                    });

            service.processStkResult(1L, "CHK-001", 0, "Success",
                    new BigDecimal("1000"), "254712345678", "RCP001", Instant.now());

            assertThat(payment.getStatus()).isEqualTo(FeePaymentStatus.SUCCEEDED);
            // Should not have been modified
            verify(feePaymentRepository, never())
                    .save(argThat(p -> p.getProviderReceipt() != null));
        }

        @Test
        @DisplayName("Amount mismatch holds payment for review")
        void amountMismatchHoldsPayment() {
            FeePayment payment = FeePayment.builder()
                    .id(1L).schoolId(1L).studentId(1L)
                    .channel(FeePaymentChannel.STK).amount(new BigDecimal("1000"))
                    .status(FeePaymentStatus.PENDING)
                    .checkoutRequestID("CHK-001")
                    .build();
            when(feePaymentRepository.findBySchoolIdAndCheckoutRequestID(
                    1L, "CHK-001")).thenReturn(Optional.of(payment));
            when(feePaymentRepository.save(any(FeePayment.class))).thenReturn(payment);
            when(inboxRepository.save(any(CallbackInbox.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.processStkResult(1L, "CHK-001", 0, "Success",
                    new BigDecimal("2000"), "254712345678", "RCP001", Instant.now());

            assertThat(payment.getStatus()).isEqualTo(FeePaymentStatus.HELD);
            assertThat(payment.getFailureReason()).contains("mismatch");
        }

        @Test
        @DisplayName("Unknown CheckoutRequestID logs warning")
        void unknownCheckoutRequestIDLogsWarning() {
            when(feePaymentRepository.findBySchoolIdAndCheckoutRequestID(
                    1L, "UNKNOWN")).thenReturn(Optional.empty());
            when(inboxRepository.save(any(CallbackInbox.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.processStkResult(1L, "UNKNOWN", 0, "Success",
                    new BigDecimal("1000"), "254712345678", "RCP001", Instant.now());

            // Should complete without error
        }
    }

    // ==================== STK Timeout Job ====================

    @Nested
    @DisplayName("StkPushTimeoutJob — stale payment resolution")
    class StkTimeoutJobTest {

        @Mock
        private FeePaymentRepository feePaymentRepository;
        @Mock
        private StkCallbackService stkCallbackService;

        private FakeMpesaClient mpesaClient;
        private StkPushTimeoutJob job;

        @org.junit.jupiter.api.BeforeEach
        void setUp() {
            mpesaClient = new FakeMpesaClient();
            Clock clock = Clock.fixed(Instant.parse("2025-01-01T12:10:00Z"), ZoneOffset.UTC);
            job = new StkPushTimeoutJob(feePaymentRepository, mpesaClient, stkCallbackService, clock);
        }

        @Test
        @DisplayName("Timeout job skips when no stale payments")
        void skipsWhenNoStalePayments() {
            when(feePaymentRepository.claimStalePayments(
                    any(FeePaymentStatus.class), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of());
            when(feePaymentRepository.returnExpiredClaims(any(LocalDateTime.class), any(FeePaymentStatus.class))).thenReturn(0);

            job.processStalePayments();

            verify(stkCallbackService, never()).resolveStalePayment(anyLong(), any(), anyString());
        }

        @Test
        @DisplayName("Timeout job returns expired claims")
        void returnsExpiredClaims() {
            when(feePaymentRepository.returnExpiredClaims(any(LocalDateTime.class), any(FeePaymentStatus.class))).thenReturn(3);
            when(feePaymentRepository.claimStalePayments(
                    any(FeePaymentStatus.class), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of());

            job.processStalePayments();

            verify(feePaymentRepository).returnExpiredClaims(any(LocalDateTime.class), any(FeePaymentStatus.class));
        }

        @Test
        @DisplayName("Timeout job claims stale payments with SKIP LOCKED")
        void claimsStalePayments() {
            FeePayment payment = FeePayment.builder()
                    .id(1L).schoolId(1L).studentId(1L)
                    .status(FeePaymentStatus.PENDING)
                    .checkoutRequestID(null)
                    .build();
            when(feePaymentRepository.claimStalePayments(
                    any(FeePaymentStatus.class), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of(payment));
            when(feePaymentRepository.returnExpiredClaims(any(LocalDateTime.class), any(FeePaymentStatus.class))).thenReturn(0);

            job.processStalePayments();

            // Verify the job used SKIP LOCKED claiming
            verify(feePaymentRepository).claimStalePayments(
                    eq(FeePaymentStatus.PENDING), any(LocalDateTime.class), any(LocalDateTime.class));
        }
    }

    // ==================== StkCallbackService — resolveStalePayment ====================

    @Nested
    @DisplayName("StkCallbackService — resolveStalePayment")
    class ResolveStalePaymentTest {

        @Mock
        private FeePaymentRepository feePaymentRepository;
        @Mock
        private CallbackInboxRepository inboxRepository;
        @Mock
        private StudentFeeAccountRepository studentFeeAccountRepository;
        @Mock
        private PaymentPostingService paymentPostingService;
        @Mock
        private LedgerService ledgerService;
        @Mock
        private SchoolPaymentConfigService configService;

        private StkCallbackService service;

        @org.junit.jupiter.api.BeforeEach
        void setUp() {
            service = new StkCallbackService(
                    inboxRepository, feePaymentRepository, studentFeeAccountRepository,
                    paymentPostingService, ledgerService, configService);
        }

        @Test
        @DisplayName("resolveStalePayment marks payment as TIMED_OUT")
        void marksPaymentAsTimedOut() {
            FeePayment payment = FeePayment.builder()
                    .id(1L).schoolId(1L).studentId(1L)
                    .status(FeePaymentStatus.PENDING).build();
            when(feePaymentRepository.save(any(FeePayment.class))).thenReturn(payment);

            service.resolveStalePayment(1L, payment, "STK Push timed out");

            assertThat(payment.getStatus()).isEqualTo(FeePaymentStatus.TIMED_OUT);
            assertThat(payment.getFailureReason()).isEqualTo("STK Push timed out");
        }

        @Test
        @DisplayName("resolveStalePayment skips already succeeded payment")
        void skipsAlreadySucceeded() {
            FeePayment payment = FeePayment.builder()
                    .id(1L).schoolId(1L).studentId(1L)
                    .status(FeePaymentStatus.SUCCEEDED).build();

            service.resolveStalePayment(1L, payment, "Should not change");

            assertThat(payment.getStatus()).isEqualTo(FeePaymentStatus.SUCCEEDED);
            verify(feePaymentRepository, never()).save(any());
        }
    }

    // ==================== DTOs ====================

    @Nested
    @DisplayName("STK DTOs")
    class StkDtoTest {

        @Test
        @DisplayName("StkPushRequestDto validates fields")
        void stkPushRequestDtoFields() {
            StkPushRequestDto dto = new StkPushRequestDto(1L, new BigDecimal("1000"), "254712345678");
            assertThat(dto.studentId()).isEqualTo(1L);
            assertThat(dto.amount()).isEqualTo(new BigDecimal("1000"));
            assertThat(dto.phone()).isEqualTo("254712345678");
        }

        @Test
        @DisplayName("StkPushResponseDto contains all fields")
        void stkPushResponseDtoFields() {
            StkPushResponseDto dto = new StkPushResponseDto(
                    1L, FeePaymentStatus.PENDING, new BigDecimal("1000"),
                    "KES", "CHK-001", "Success", Instant.now());
            assertThat(dto.paymentId()).isEqualTo(1L);
            assertThat(dto.status()).isEqualTo(FeePaymentStatus.PENDING);
            assertThat(dto.checkoutRequestID()).isEqualTo("CHK-001");
        }
    }

    // ==================== FeePayment Entity ====================

    @Nested
    @DisplayName("FeePayment — checkoutRequestID field")
    class FeePaymentEntityTest {

        @Test
        @DisplayName("FeePayment has checkoutRequestID field")
        void hasCheckoutRequestID() {
            FeePayment payment = FeePayment.builder()
                    .checkoutRequestID("CHK-001").build();
            assertThat(payment.getCheckoutRequestID()).isEqualTo("CHK-001");
        }
    }
}
