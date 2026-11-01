package com.peterscode.edutrack.fees;

import com.peterscode.edutrack.fees.dto.AllocatePaymentRequestDto;
import com.peterscode.edutrack.fees.dto.CreatePaymentConfigRequestDto;
import com.peterscode.edutrack.fees.dto.UnallocatedPaymentView;
import com.peterscode.edutrack.fees.entity.*;
import com.peterscode.edutrack.fees.enums.*;
import com.peterscode.edutrack.fees.exception.FeesRuleException;
import com.peterscode.edutrack.fees.repository.*;
import com.peterscode.edutrack.fees.service.*;
import com.peterscode.edutrack.fees.security.FeesAccessGuard;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 4 unit tests: School payment config, credential encryption, callback inbox,
 * C2B matching, and unallocated payment queue.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeesPhase4Test {

    private UserPrincipal principal(Role role, Long schoolId) {
        return UserPrincipal.builder()
                .id(1L)
                .email("test@example.com")
                .role(role)
                .schoolId(schoolId)
                .build();
    }

    // ==================== Credential Encryption ====================

    @Nested
    @DisplayName("CredentialEncryptionService — AES-GCM encryption")
    class CredentialEncryptionTest {

        @Test
        @DisplayName("Encrypt and decrypt round-trip")
        void encryptDecryptRoundTrip() {
            String key = CredentialEncryptionService.generateMasterKey();
            CredentialEncryptionService service = new CredentialEncryptionService(key);
            String plaintext = "my-secret-consumer-key";
            String encrypted = service.encrypt(plaintext);
            assertThat(encrypted).isNotEqualTo(plaintext);
            assertThat(service.decrypt(encrypted)).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("Each encryption produces different ciphertext")
        void differentCiphertextPerEncryption() {
            String key = CredentialEncryptionService.generateMasterKey();
            CredentialEncryptionService service = new CredentialEncryptionService(key);
            String plaintext = "same-value";
            String enc1 = service.encrypt(plaintext);
            String enc2 = service.encrypt(plaintext);
            assertThat(enc1).isNotEqualTo(enc2);
            assertThat(service.decrypt(enc1)).isEqualTo(plaintext);
            assertThat(service.decrypt(enc2)).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("Null input returns null")
        void nullInputReturnsNull() {
            String key = CredentialEncryptionService.generateMasterKey();
            CredentialEncryptionService service = new CredentialEncryptionService(key);
            assertThat(service.encrypt(null)).isNull();
            assertThat(service.decrypt(null)).isNull();
        }

        @Test
        @DisplayName("Invalid key length throws exception")
        void invalidKeyLengthThrows() {
            assertThatThrownBy(() -> new CredentialEncryptionService("YWJj"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Empty key throws exception")
        void emptyKeyThrows() {
            assertThatThrownBy(() -> new CredentialEncryptionService(""))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ==================== Payment Matching ====================

    @Nested
    @DisplayName("PaymentMatchingService — admission number normalization")
    class PaymentMatchingTest {

        @Mock
        private StudentRepository studentRepository;

        @Test
        @DisplayName("Normalize strips spaces, hyphens, uppercases")
        void normalizeStripsSpacesAndHyphens() {
            assertThat(PaymentMatchingService.normalizeAccountReference("  ADM-001  "))
                    .isEqualTo("ADM001");
            assertThat(PaymentMatchingService.normalizeAccountReference("adm 001"))
                    .isEqualTo("ADM001");
        }

        @Test
        @DisplayName("Null returns empty string")
        void nullReturnsEmpty() {
            assertThat(PaymentMatchingService.normalizeAccountReference(null)).isEmpty();
        }

        @Test
        @DisplayName("Match finds active student by admission number")
        void matchFindsActiveStudent() {
            PaymentMatchingService service = new PaymentMatchingService(studentRepository);
            Student student = Student.builder()
                    .schoolId(1L).id(100L)
                    .admissionNumber("ADM001")
                    .status(StudentStatus.ACTIVE)
                    .build();
            when(studentRepository.findBySchoolIdAndAdmissionNumber(1L, "ADM001"))
                    .thenReturn(Optional.of(student));

            Optional<PaymentMatchingService.StudentMatch> result =
                    service.findStudentByAccountReference(1L, "  adm-001  ");

            assertThat(result.isPresent()).isTrue();
            assertThat(result.get().studentId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("No match returns empty")
        void noMatchReturnsEmpty() {
            PaymentMatchingService service = new PaymentMatchingService(studentRepository);
            when(studentRepository.findBySchoolIdAndAdmissionNumber(1L, "ADM999"))
                    .thenReturn(Optional.empty());

            Optional<PaymentMatchingService.StudentMatch> result =
                    service.findStudentByAccountReference(1L, "ADM999");

            assertThat(result.isPresent()).isFalse();
        }

        @Test
        @DisplayName("Inactive student returns empty (queued for manual resolution)")
        void inactiveStudentReturnsEmpty() {
            PaymentMatchingService service = new PaymentMatchingService(studentRepository);
            Student student = Student.builder()
                    .schoolId(1L).id(100L)
                    .admissionNumber("ADM001")
                    .status(StudentStatus.INACTIVE)
                    .build();
            when(studentRepository.findBySchoolIdAndAdmissionNumber(1L, "ADM001"))
                    .thenReturn(Optional.of(student));

            Optional<PaymentMatchingService.StudentMatch> result =
                    service.findStudentByAccountReference(1L, "ADM001");

            assertThat(result.isPresent()).isFalse();
        }
    }

    // ==================== School Payment Config ====================

    @Nested
    @DisplayName("SchoolPaymentConfigService — config management")
    class PaymentConfigTest {

        @Mock
        private SchoolPaymentConfigRepository configRepository;
        @Mock
        private CredentialEncryptionService encryptionService;

        @Test
        @DisplayName("SCHOOL_ADMIN can view config")
        void schoolAdminCanViewConfig() {
            SchoolPaymentConfigService service = new SchoolPaymentConfigService(
                    configRepository, encryptionService);
            when(configRepository.findBySchoolId(1L)).thenReturn(Optional.empty());
            Optional<SchoolPaymentConfig> result = service.getConfig(principal(Role.SCHOOL_ADMIN, 1L));
            assertThat(result.isPresent()).isFalse();
        }

        @Test
        @DisplayName("ACCOUNTANT can view config")
        void accountantCanViewConfig() {
            SchoolPaymentConfigService service = new SchoolPaymentConfigService(
                    configRepository, encryptionService);
            when(configRepository.findBySchoolId(1L)).thenReturn(Optional.empty());
            Optional<SchoolPaymentConfig> result = service.getConfig(principal(Role.ACCOUNTANT, 1L));
            assertThat(result.isPresent()).isFalse();
        }

        @Test
        @DisplayName("PARENT cannot view config")
        void parentCannotViewConfig() {
            SchoolPaymentConfigService service = new SchoolPaymentConfigService(
                    configRepository, encryptionService);
            assertThatThrownBy(() -> service.getConfig(principal(Role.PARENT, 1L)))
                    .isInstanceOf(FeesRuleException.class)
                    .extracting("errorCode").isEqualTo("INSUFFICIENT_PERMISSION");
        }

        @Test
        @DisplayName("Shortcode uniqueness is checked")
        void shortcodeUniquenessChecked() {
            SchoolPaymentConfigService service = new SchoolPaymentConfigService(
                    configRepository, encryptionService);
            when(configRepository.existsByShortcodeAndSchoolIdNot("123456", 1L))
                    .thenReturn(true);
            when(encryptionService.encrypt(any())).thenReturn("encrypted");

            assertThatThrownBy(() -> service.upsertConfig(
                    principal(Role.SCHOOL_ADMIN, 1L), "123456",
                    ShortcodeType.PAYBILL, MpesaEnvironment.SANDBOX,
                    "key", "secret", "passkey"))
                    .isInstanceOf(FeesRuleException.class)
                    .extracting("errorCode").isEqualTo("CONFIG_INVALID");
        }

        @Test
        @DisplayName("Token rotation generates new token")
        void tokenRotationGeneratesNewToken() {
            SchoolPaymentConfigService service = new SchoolPaymentConfigService(
                    configRepository, encryptionService);
            SchoolPaymentConfig config = SchoolPaymentConfig.builder()
                    .schoolId(1L).callbackTokenHash("old-hash").build();
            when(configRepository.findBySchoolId(1L)).thenReturn(Optional.of(config));
            when(configRepository.save(any())).thenReturn(config);

            String token = service.rotateCallbackToken(principal(Role.SCHOOL_ADMIN, 1L));
            assertThat(token).isNotBlank();
            assertThat(config.getCallbackTokenHash()).isNotEqualTo("old-hash");
        }
    }

    // ==================== Unallocated Payment Service ====================

    @Nested
    @DisplayName("UnallocatedPaymentService — manual resolution")
    class UnallocatedPaymentTest {

        @Mock
        private UnallocatedPaymentRepository unallocatedRepo;
        @Mock
        private StudentRepository studentRepository;
        @Mock
        private FeePaymentRepository feePaymentRepository;
        @Mock
        private StudentFeeAccountRepository accountRepo;
        @Mock
        private PaymentPostingService paymentPostingService;
        @Mock
        private LedgerService ledgerService;

        private UnallocatedPaymentService createService() {
            return new UnallocatedPaymentService(
                    unallocatedRepo, studentRepository, feePaymentRepository,
                    accountRepo, paymentPostingService, ledgerService);
        }

        @Test
        @DisplayName("ACCOUNTANT can list unallocated payments")
        void accountantCanListUnallocated() {
            UnallocatedPaymentService service = createService();
            when(unallocatedRepo.findBySchoolIdAndStatus(eq(1L), eq(UnallocatedPaymentStatus.PENDING), any(Pageable.class)))
                    .thenReturn(Page.empty());
            Page<UnallocatedPaymentView> result = service.listUnallocated(principal(Role.ACCOUNTANT, 1L), Pageable.unpaged());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("PARENT cannot list unallocated payments")
        void parentCannotListUnallocated() {
            UnallocatedPaymentService service = createService();
            assertThatThrownBy(() -> service.listUnallocated(principal(Role.PARENT, 1L), Pageable.unpaged()))
                    .isInstanceOf(FeesRuleException.class)
                    .extracting("errorCode").isEqualTo("INSUFFICIENT_PERMISSION");
        }

        @Test
        @DisplayName("Allocate to student updates status")
        void allocateToStudentUpdatesStatus() {
            UnallocatedPaymentService service = createService();
            UnallocatedPayment unallocated = UnallocatedPayment.builder()
                    .id(1L).schoolId(1L).amount(new BigDecimal("5000"))
                    .channel(FeePaymentChannel.PAYBILL)
                    .status(UnallocatedPaymentStatus.PENDING)
                    .currency("KES")
                    .build();
            Student student = Student.builder().id(100L).schoolId(1L).build();
            StudentFeeAccount account = StudentFeeAccount.builder()
                    .schoolId(1L).studentId(100L).currency("KES").build();

            when(unallocatedRepo.findBySchoolIdAndIdAndStatus(1L, 1L, UnallocatedPaymentStatus.PENDING))
                    .thenReturn(Optional.of(unallocated));
            when(studentRepository.findBySchoolIdAndId(1L, 100L))
                    .thenReturn(Optional.of(student));
            when(feePaymentRepository.existsBySchoolIdAndChannelAndProviderReceipt(1L, FeePaymentChannel.PAYBILL, null))
                    .thenReturn(false);
            when(accountRepo.findBySchoolIdAndStudentId(1L, 100L))
                    .thenReturn(Optional.of(account));
            when(feePaymentRepository.save(any())).thenReturn(new FeePayment());
            when(unallocatedRepo.save(any())).thenReturn(unallocated);

            UnallocatedPaymentView result = service.allocateToStudent(
                    principal(Role.ACCOUNTANT, 1L), 1L, 100L, "Manual allocation");

            assertThat(result.status()).isEqualTo(UnallocatedPaymentStatus.ALLOCATED);
            assertThat(result.matchedStudentId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("Mark for refund denies non-admin roles")
        void markForRefundDeniesNonAdmin() {
            UnallocatedPaymentService service = createService();
            assertThatThrownBy(() -> service.markForRefund(
                    principal(Role.TEACHER, 1L), 1L, "Refund"))
                    .isInstanceOf(FeesRuleException.class)
                    .extracting("errorCode").isEqualTo("INSUFFICIENT_PERMISSION");
        }
    }

    // ==================== Enum Tests ====================

    @Nested
    @DisplayName("Phase 4 enums")
    class EnumTest {

        @Test
        @DisplayName("ShortcodeType has PAYBILL and TILL")
        void shortcodeTypeValues() {
            assertThat(ShortcodeType.values()).hasSize(2);
            assertThat(ShortcodeType.valueOf("PAYBILL")).isEqualTo(ShortcodeType.PAYBILL);
            assertThat(ShortcodeType.valueOf("TILL")).isEqualTo(ShortcodeType.TILL);
        }

        @Test
        @DisplayName("MpesaEnvironment has SANDBOX and PRODUCTION")
        void mpesaEnvironmentValues() {
            assertThat(MpesaEnvironment.values()).hasSize(2);
        }

        @Test
        @DisplayName("CallbackChannel has STK, C2B_CONFIRMATION, C2B_VALIDATION")
        void callbackChannelValues() {
            assertThat(CallbackChannel.values()).hasSize(3);
        }

        @Test
        @DisplayName("UnallocatedPaymentStatus has 4 values")
        void unallocatedPaymentStatusValues() {
            assertThat(UnallocatedPaymentStatus.values()).hasSize(4);
        }
    }

    // ==================== DTO Tests ====================

    @Nested
    @DisplayName("Phase 4 DTOs")
    class DtoTest {

        @Test
        @DisplayName("CreatePaymentConfigRequestDto fields")
        void createPaymentConfigRequestDtoFields() {
            CreatePaymentConfigRequestDto dto = new CreatePaymentConfigRequestDto(
                    "123456", ShortcodeType.PAYBILL, MpesaEnvironment.SANDBOX,
                    "key", "secret", "passkey");
            assertThat(dto.shortcode()).isEqualTo("123456");
            assertThat(dto.shortcodeType()).isEqualTo(ShortcodeType.PAYBILL);
        }

        @Test
        @DisplayName("AllocatePaymentRequestDto fields")
        void allocatePaymentRequestDtoFields() {
            AllocatePaymentRequestDto dto = new AllocatePaymentRequestDto(100L, "Reason");
            assertThat(dto.studentId()).isEqualTo(100L);
            assertThat(dto.reason()).isEqualTo("Reason");
        }
    }
}
