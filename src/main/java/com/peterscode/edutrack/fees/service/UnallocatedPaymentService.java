package com.peterscode.edutrack.fees.service;

import com.peterscode.edutrack.fees.dto.UnallocatedPaymentView;
import com.peterscode.edutrack.fees.entity.FeePayment;
import com.peterscode.edutrack.fees.entity.StudentFeeAccount;
import com.peterscode.edutrack.fees.entity.UnallocatedPayment;
import com.peterscode.edutrack.fees.enums.FeePaymentChannel;
import com.peterscode.edutrack.fees.enums.FeePaymentStatus;
import com.peterscode.edutrack.fees.enums.UnallocatedPaymentStatus;
import com.peterscode.edutrack.fees.exception.FeesRuleException;
import com.peterscode.edutrack.fees.repository.FeePaymentRepository;
import com.peterscode.edutrack.fees.repository.StudentFeeAccountRepository;
import com.peterscode.edutrack.fees.repository.UnallocatedPaymentRepository;
import com.peterscode.edutrack.fees.security.FeesAccessGuard;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.springframework.http.HttpStatus.*;

/**
 * Manages unallocated payments — payments that arrived via C2B but couldn't be
 * auto-matched to a student. ACCOUNTANT and SCHOOL_ADMIN can list, allocate,
 * or mark for refund.
 */
@Service
@RequiredArgsConstructor
public class UnallocatedPaymentService {

    private final UnallocatedPaymentRepository unallocatedRepo;
    private final StudentRepository studentRepository;
    private final FeePaymentRepository feePaymentRepository;
    private final StudentFeeAccountRepository studentFeeAccountRepository;
    private final PaymentPostingService paymentPostingService;
    private final LedgerService ledgerService;

    /**
     * List unallocated payments for the current school.
     */
    @Transactional(readOnly = true)
    public Page<UnallocatedPaymentView> listUnallocated(UserPrincipal principal,
            Pageable pageable) {
        FeesAccessGuard.assertAdminOrAccountant(principal);
        Long schoolId = principal.getSchoolId();
        return unallocatedRepo.findBySchoolIdAndStatus(schoolId,
                UnallocatedPaymentStatus.PENDING, pageable).map(this::toView);
    }

    /**
     * List all unallocated payments (including resolved) for audit.
     */
    @Transactional(readOnly = true)
    public Page<UnallocatedPaymentView> listAll(UserPrincipal principal,
            Pageable pageable) {
        FeesAccessGuard.assertAdminOrAccountant(principal);
        Long schoolId = principal.getSchoolId();
        return unallocatedRepo.findBySchoolId(schoolId, pageable).map(this::toView);
    }

    /**
     * Allocate an unallocated payment to a specific student.
     * Creates a FeePayment and posts to the ledger.
     */
    @Transactional
    public UnallocatedPaymentView allocateToStudent(UserPrincipal principal,
            Long unallocatedId, Long studentId, String reason) {
        FeesAccessGuard.assertAdminOrAccountant(principal);
        Long schoolId = principal.getSchoolId();

        UnallocatedPayment unallocated = unallocatedRepo
                .findBySchoolIdAndIdAndStatus(schoolId, unallocatedId,
                        UnallocatedPaymentStatus.PENDING)
                .orElseThrow(() -> new FeesRuleException("RESOURCE_NOT_FOUND", NOT_FOUND,
                        "Unallocated payment not found or already resolved"));

        // Verify student belongs to the school
        Student student = studentRepository.findBySchoolIdAndId(schoolId, studentId)
                .orElseThrow(() -> new FeesRuleException("RESOURCE_NOT_FOUND", NOT_FOUND,
                        "Student not found"));

        // Check idempotency — don't post the same provider receipt twice
        if (unallocated.getProviderReceipt() != null) {
            boolean exists = feePaymentRepository.existsBySchoolIdAndChannelAndProviderReceipt(
                    schoolId, FeePaymentChannel.PAYBILL, unallocated.getProviderReceipt());
            if (exists) {
                throw new FeesRuleException("UNALLOCATED_RESOLVED", CONFLICT,
                        "Payment already posted");
            }
        }

        // Ensure fee account exists
        StudentFeeAccount account = studentFeeAccountRepository
                .findBySchoolIdAndStudentId(schoolId, studentId)
                .orElseGet(() -> studentFeeAccountRepository.save(
                        StudentFeeAccount.builder()
                                .schoolId(schoolId)
                                .studentId(studentId)
                                .build()));

        // Create and post the payment
        FeePayment payment = FeePayment.builder()
                .schoolId(schoolId)
                .studentId(studentId)
                .channel(unallocated.getChannel())
                .amount(unallocated.getAmount())
                .currency(unallocated.getCurrency())
                .providerReceipt(unallocated.getProviderReceipt())
                .payerPhoneMasked(unallocated.getPayerPhoneMasked())
                .accountReference(unallocated.getAccountReference())
                .status(FeePaymentStatus.SUCCEEDED)
                .confirmedAt(Instant.now())
                .build();

        payment = feePaymentRepository.save(payment);
        paymentPostingService.allocatePaymentToInvoices(account, payment);
        ledgerService.updateCachedBalance(account);
        studentFeeAccountRepository.save(account);

        // Mark unallocated as resolved
        unallocated.setStatus(UnallocatedPaymentStatus.ALLOCATED);
        unallocated.setMatchedStudentId(studentId);
        unallocated.setMatchedAt(Instant.now());
        unallocated.setReason(reason != null ? reason : "Manually allocated by " + principal.getEmail());
        unallocated.setResolvedBy(principal.getId());
        unallocated.setResolvedAt(Instant.now());
        unallocatedRepo.save(unallocated);

        return toView(unallocated);
    }

    /**
     * Mark an unallocated payment for refund.
     */
    @Transactional
    public UnallocatedPaymentView markForRefund(UserPrincipal principal,
            Long unallocatedId, String reason) {
        FeesAccessGuard.assertAdmin(principal);
        Long schoolId = principal.getSchoolId();

        UnallocatedPayment unallocated = unallocatedRepo
                .findBySchoolIdAndIdAndStatus(schoolId, unallocatedId,
                        UnallocatedPaymentStatus.PENDING)
                .orElseThrow(() -> new FeesRuleException("RESOURCE_NOT_FOUND", NOT_FOUND,
                        "Unallocated payment not found or already resolved"));

        unallocated.setStatus(UnallocatedPaymentStatus.REFUND_REQUESTED);
        unallocated.setReason(reason != null ? reason : "Refund requested by " + principal.getEmail());
        unallocated.setResolvedBy(principal.getId());
        unallocated.setResolvedAt(Instant.now());
        unallocatedRepo.save(unallocated);

        return toView(unallocated);
    }

    private UnallocatedPaymentView toView(UnallocatedPayment u) {
        return new UnallocatedPaymentView(
                u.getId(), u.getAmount(), u.getCurrency(), u.getChannel(),
                u.getProviderReceipt(), u.getAccountReference(),
                u.getPayerPhoneMasked(), u.getStatus(), u.getReason(),
                u.getMatchedStudentId(), u.getResolvedAt(), u.getCreatedAt());
    }
}
