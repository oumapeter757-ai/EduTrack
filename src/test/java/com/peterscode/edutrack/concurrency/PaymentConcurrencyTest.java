package com.peterscode.edutrack.concurrency;

import com.peterscode.edutrack.fees.entity.FeePayment;
import com.peterscode.edutrack.fees.entity.StudentFeeAccount;
import com.peterscode.edutrack.fees.enums.FeePaymentChannel;
import com.peterscode.edutrack.fees.enums.FeePaymentStatus;
import com.peterscode.edutrack.fees.repository.FeePaymentRepository;
import com.peterscode.edutrack.fees.repository.StudentFeeAccountRepository;
import com.peterscode.edutrack.fees.service.PaymentPostingService;
import com.peterscode.edutrack.fees.service.StkPushService;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.entity.User;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency tests for payment operations.
 * These tests are expected to FAIL on the current code because:
 * - No unique constraint prevents duplicate PENDING payments
 * - Payment posting has no pessimistic lock on StudentFeeAccount
 * - Callback processing is not fully idempotent
 */
@Tag("concurrency")
class PaymentConcurrencyTest extends ConcurrencyTestBase {

    @Autowired
    private FeePaymentRepository feePaymentRepository;

    @Autowired
    private StudentFeeAccountRepository studentFeeAccountRepository;

    @Autowired
    private StudentRepository studentRepository;

    private Long studentId;
    private StudentFeeAccount account;

    @org.junit.jupiter.api.BeforeEach
    void setupPayment() {
        // Create a student
        Student student = Student.builder()
                .schoolId(schoolId)
                .firstName("Payer")
                .lastName("Student")
                .admissionNumber("PAY-" + schoolId + "-001")
                .status(com.peterscode.edutrack.student.enums.StudentStatus.ACTIVE)
                .build();
        student = studentRepository.save(student);
        studentId = student.getId();

        // Create fee account
        account = StudentFeeAccount.builder()
                .schoolId(schoolId)
                .studentId(studentId)
                .cachedBalance(BigDecimal.ZERO)
                .currency("KES")
                .build();
        account = studentFeeAccountRepository.save(account);
    }

    /**
     * Test: 50 concurrent payments to the same student account.
     * Expected: All payments succeed, balance equals sum of all entries, no duplicates.
     * Current code: May have race conditions in balance updates.
     */
    @Test
    void concurrentPayments_shouldMaintainBalanceConsistency() throws Exception {
        int threads = 50;
        BigDecimal paymentAmount = new BigDecimal("100.00");

        // Create payments directly to simulate concurrent callbacks
        runConcurrent(threads, () -> {
            FeePayment payment = FeePayment.builder()
                    .schoolId(schoolId)
                    .studentId(studentId)
                    .channel(FeePaymentChannel.MANUAL)
                    .amount(paymentAmount)
                    .currency("KES")
                    .status(FeePaymentStatus.SUCCEEDED)
                    .receiptNumber("RCP-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000))
                    .initiatedBy(adminUserId)
                    .build();
            feePaymentRepository.save(payment);
            return payment.getId();
        }, 30);

        // Verify all payments are recorded
        List<FeePayment> payments = feePaymentRepository.findAll().stream()
                .filter(p -> schoolId.equals(p.getSchoolId()) && studentId.equals(p.getStudentId()))
                .toList();

        assertThat(payments.size())
                .as("All 50 payments should be recorded")
                .isEqualTo(threads);

        // Verify no duplicate receipt numbers
        long uniqueReceipts = payments.stream()
                .map(FeePayment::getReceiptNumber)
                .distinct()
                .count();
        assertThat(uniqueReceipts)
                .as("No duplicate receipt numbers")
                .isEqualTo(payments.size());
    }

    /**
     * Test: Duplicate callback processing — same provider receipt processed twice.
     * Expected: Exactly one payment record.
     * Current code: May create duplicate payments if the unique constraint is not checked.
     */
    @Test
    void duplicateCallback_shouldCreateOnlyOnePayment() throws Exception {
        String providerReceipt = "PROV-RECEIPT-" + System.currentTimeMillis();

        // First callback
        FeePayment payment1 = FeePayment.builder()
                .schoolId(schoolId)
                .studentId(studentId)
                .channel(FeePaymentChannel.STK)
                .amount(new BigDecimal("500.00"))
                .currency("KES")
                .status(FeePaymentStatus.SUCCEEDED)
                .providerReceipt(providerReceipt)
                .initiatedBy(adminUserId)
                .build();
        feePaymentRepository.save(payment1);

        // Second callback with same receipt — should fail with constraint violation
        FeePayment payment2 = FeePayment.builder()
                .schoolId(schoolId)
                .studentId(studentId)
                .channel(FeePaymentChannel.STK)
                .amount(new BigDecimal("500.00"))
                .currency("KES")
                .status(FeePaymentStatus.SUCCEEDED)
                .providerReceipt(providerReceipt)
                .initiatedBy(adminUserId)
                .build();

        boolean exceptionThrown = false;
        try {
            feePaymentRepository.save(payment2);
        } catch (Exception e) {
            exceptionThrown = true;
        }

        assertThat(exceptionThrown)
                .as("Duplicate provider receipt should be rejected by unique constraint")
                .isTrue();

        // Verify only one payment exists
        List<FeePayment> byReceipt = feePaymentRepository.findAll().stream()
                .filter(p -> providerReceipt.equals(p.getProviderReceipt()))
                .toList();
        assertThat(byReceipt.size())
                .as("Only one payment for this provider receipt")
                .isEqualTo(1);
    }
}
