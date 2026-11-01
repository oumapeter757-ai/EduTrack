package com.peterscode.edutrack.fees.service;

import com.peterscode.edutrack.fees.dto.StkPushRequestDto;
import com.peterscode.edutrack.fees.dto.StkPushResponseDto;
import com.peterscode.edutrack.fees.entity.FeePayment;
import com.peterscode.edutrack.fees.entity.StudentFeeAccount;
import com.peterscode.edutrack.fees.enums.FeePaymentChannel;
import com.peterscode.edutrack.fees.enums.FeePaymentStatus;
import com.peterscode.edutrack.fees.exception.FeesRuleException;
import com.peterscode.edutrack.fees.mpesa.MpesaClient;
import com.peterscode.edutrack.fees.repository.FeePaymentRepository;
import com.peterscode.edutrack.fees.repository.StudentFeeAccountRepository;
import com.peterscode.edutrack.fees.security.FeesAccessGuard;
import com.peterscode.edutrack.fees.service.PaymentPostingService;
import com.peterscode.edutrack.guardian.entity.Guardian;
import com.peterscode.edutrack.guardian.repository.GuardianRepository;
import com.peterscode.edutrack.fees.mpesa.MpesaClient.StkPushResult;
import com.peterscode.edutrack.security.ParentAccessGuard;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.user.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.springframework.http.HttpStatus.*;

/**
 * Handles STK Push payment initiation.
 * <p>
 * Flow:
 * 1. Validate request (amount, phone, student)
 * 2. Check no pending STK payment exists for this student
 * 3. Create a PENDING FeePayment record
 * 4. Call MpesaClient to initiate STK Push
 * 5. Store CheckoutRequestID on the payment
 * 6. Return PENDING status to the client for polling
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StkPushService {

    private final MpesaClient mpesaClient;
    private final FeePaymentRepository feePaymentRepository;
    private final StudentRepository studentRepository;
    private final StudentFeeAccountRepository studentFeeAccountRepository;
    private final GuardianRepository guardianRepository;
    private final Clock clock;

    @Value("${edutrack.fees.stk-max-amount:150000}")
    private BigDecimal maxAmount;

    @Value("${edutrack.fees.stk-timeout-minutes:5}")
    private int timeoutMinutes;

    @Value("${mpesa.stk-callback-url:https://localhost:8080/api/v1/fees/callback/stk}")
    private String stkCallbackUrl;

    /**
     * Initiate an STK Push payment.
     *
     * @param principal the authenticated user (PARENT or STUDENT)
     * @param request   the STK push request with studentId, amount, and optional phone
     * @return response with paymentId and PENDING status
     */
    @Transactional
    public StkPushResponseDto initiate(UserPrincipal principal, StkPushRequestDto request) {
        if (principal.getRole() != Role.PARENT && principal.getRole() != Role.STUDENT) {
            throw new FeesRuleException("INSUFFICIENT_PERMISSION", FORBIDDEN,
                    "Only parents and students can initiate STK payments");
        }
        Long schoolId = principal.getSchoolId();

        // Resolve student ID
        Long studentId = null;
        if (principal.getRole() == Role.STUDENT) {
            Student student = studentRepository.findBySchoolIdAndUserId(schoolId, principal.getId())
                    .orElseThrow(() -> new FeesRuleException("RESOURCE_NOT_FOUND", NOT_FOUND,
                            "Student not found"));
            studentId = student.getId();
        } else if (principal.getRole() == Role.PARENT) {
            studentId = request.studentId();
        }

        // Verify student exists and belongs to this school
        Student student = studentRepository.findBySchoolIdAndId(schoolId, studentId)
                .orElseThrow(() -> new FeesRuleException("RESOURCE_NOT_FOUND", NOT_FOUND,
                        "Student not found"));

        // Validate amount
        validateAmount(request.amount());

        // Resolve phone number
        String phone = resolvePhone(principal, request.phone());
        validatePhone(phone);

        // Check no pending STK payment for this student
        Optional<FeePayment> pending = feePaymentRepository
                .findBySchoolIdAndStudentIdAndStatus(schoolId, studentId, FeePaymentStatus.PENDING);
        if (pending.isPresent()) {
            throw new FeesRuleException("PAYMENT_ALREADY_PENDING", CONFLICT,
                    "A pending payment already exists for this student");
        }

        // Generate CheckoutRequestID placeholder — will be set after Mpesa call
        String accountReference = "STK-" + schoolId + "-" + studentId;

        // Create PENDING payment record first
        FeePayment payment = FeePayment.builder()
                .schoolId(schoolId)
                .studentId(studentId)
                .channel(FeePaymentChannel.STK)
                .amount(request.amount())
                .currency("KES")
                .payerPhoneMasked(maskPhone(phone))
                .accountReference(accountReference)
                .status(FeePaymentStatus.PENDING)
                .initiatedBy(principal.getId())
                .build();
        payment = feePaymentRepository.save(payment);

        // Call Mpesa to initiate STK Push
        try {
            StkPushResult result = mpesaClient.initiateStkPush(
                    phone, request.amount(),
                    stkCallbackUrl + "/" + generateCallbackTokenPath(schoolId),
                    accountReference,
                    "EduTrack Fee Payment");

            if (result.success()) {
                payment.setCheckoutRequestID(result.checkoutRequestID());
                payment = feePaymentRepository.save(payment);
                log.info("STK Push initiated: paymentId={}, checkoutRequestID={}",
                        payment.getId(), result.checkoutRequestID());
            } else {
                payment.setStatus(FeePaymentStatus.FAILED);
                payment.setFailureReason(result.customerMessage());
                payment = feePaymentRepository.save(payment);
                log.warn("STK Push initiation failed: paymentId={}, reason={}",
                        payment.getId(), result.customerMessage());
            }

            java.time.Instant createdInstant = payment.getCreatedAt() != null
                    ? payment.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toInstant()
                    : null;
            return new StkPushResponseDto(
                    payment.getId(),
                    payment.getStatus(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getCheckoutRequestID(),
                    result.customerMessage(),
                    createdInstant
            );
        } catch (Exception e) {
            log.error("STK Push API call failed", e);
            payment.setStatus(FeePaymentStatus.FAILED);
            payment.setFailureReason("Mpesa API error: " + e.getMessage());
            feePaymentRepository.save(payment);
            throw new FeesRuleException("PAYMENT_FAILED", INTERNAL_SERVER_ERROR,
                    "Failed to initiate STK Push");
        }
    }

    /**
     * Get the status of a payment by ID. Used for polling after STK initiation.
     */
    @Transactional(readOnly = true)
    public FeePayment getPaymentStatus(UserPrincipal principal, Long paymentId) {
        Long schoolId = principal.getSchoolId();

        // PARENT can check their linked children's payments
        // STUDENT can check their own payments
        // SCHOOL_ADMIN/ACCOUNTANT can check any payment
        if (principal.getRole() == Role.STUDENT) {
            Student student = studentRepository.findBySchoolIdAndUserId(schoolId, principal.getId())
                    .orElseThrow(() -> new FeesRuleException("RESOURCE_NOT_FOUND", NOT_FOUND,
                            "Student not found"));
            FeePayment payment = feePaymentRepository.findBySchoolIdAndId(schoolId, paymentId)
                    .orElseThrow(() -> new FeesRuleException("RESOURCE_NOT_FOUND", NOT_FOUND,
                            "Payment not found"));
            if (!payment.getStudentId().equals(student.getId())) {
                throw new FeesRuleException("INSUFFICIENT_PERMISSION", FORBIDDEN,
                        "Access denied");
            }
            return payment;
        }

        return feePaymentRepository.findBySchoolIdAndId(schoolId, paymentId)
                .orElseThrow(() -> new FeesRuleException("RESOURCE_NOT_FOUND", NOT_FOUND,
                        "Payment not found"));
    }

    /**
     * Get the timeout threshold for stale PENDING payments.
     */
    public Instant getTimeoutThreshold() {
        return Instant.now(clock).minusSeconds(timeoutMinutes * 60L);
    }

    private void validateAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new FeesRuleException("INVALID_AMOUNT", BAD_REQUEST,
                    "Amount must be greater than zero");
        }
        if (maxAmount != null && amount.compareTo(maxAmount) > 0) {
            throw new FeesRuleException("INVALID_AMOUNT", BAD_REQUEST,
                    "Amount exceeds maximum allowed: " + maxAmount);
        }
    }

    private void validatePhone(String phone) {
        if (phone == null || !phone.matches("^254[0-9]{9}$")) {
            throw new FeesRuleException("INVALID_PHONE", BAD_REQUEST,
                    "Phone must be 12 digits starting with 254");
        }
    }

    private String resolvePhone(UserPrincipal principal, String requestPhone) {
        if (requestPhone != null && !requestPhone.isBlank()) {
            return requestPhone;
        }
        // Default to the user's profile phone
        if (principal.getRole() == Role.PARENT) {
            Guardian guardian = guardianRepository.findBySchoolIdAndUserId(
                    principal.getSchoolId(), principal.getId())
                    .orElseThrow(() -> new FeesRuleException("RESOURCE_NOT_FOUND", NOT_FOUND,
                            "Guardian profile not found"));
            return guardian.getPhone();
        }
        throw new FeesRuleException("INVALID_PHONE", BAD_REQUEST,
                "Phone number is required");
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() <= 4) return phone;
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }

    private String generateCallbackTokenPath(Long schoolId) {
        // Placeholder — in production this would use the school's callback token
        return String.valueOf(schoolId);
    }
}
