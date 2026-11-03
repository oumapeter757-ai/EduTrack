package com.peterscode.edutrack.fees.service;

import com.peterscode.edutrack.fees.entity.CallbackInbox;
import com.peterscode.edutrack.fees.entity.FeePayment;
import com.peterscode.edutrack.fees.entity.SchoolPaymentConfig;
import com.peterscode.edutrack.fees.entity.StudentFeeAccount;
import com.peterscode.edutrack.fees.enums.CallbackChannel;
import com.peterscode.edutrack.fees.enums.FeePaymentStatus;
import com.peterscode.edutrack.fees.exception.FeesRuleException;
import com.peterscode.edutrack.fees.repository.CallbackInboxRepository;
import com.peterscode.edutrack.fees.repository.FeePaymentRepository;
import com.peterscode.edutrack.fees.repository.StudentFeeAccountRepository;
import com.peterscode.edutrack.fees.service.LedgerService;
import com.peterscode.edutrack.fees.service.PaymentPostingService;
import com.peterscode.edutrack.fees.service.SchoolPaymentConfigService;
import com.peterscode.edutrack.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.springframework.http.HttpStatus.*;

/**
 * Processes STK Push result callbacks from Safaricom.
 * <p>
 * Flow:
 * 1. Callback arrives → save to CallbackInbox (durable storage)
 * 2. Look up school by callback token
 * 3. Match the callback to the PENDING payment by CheckoutRequestID
 * 4. If ResultCode == 0: post the payment to the ledger
 * 5. If ResultCode != 0: mark the payment as FAILED
 * 6. Mark inbox entry as processed
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StkCallbackService {

    private final CallbackInboxRepository inboxRepository;
    private final FeePaymentRepository feePaymentRepository;
    private final StudentFeeAccountRepository studentFeeAccountRepository;
    private final PaymentPostingService paymentPostingService;
    private final LedgerService ledgerService;
    private final SchoolPaymentConfigService configService;

    /**
     * Process an STK Push result callback.
     *
     * @param schoolId          the school identified by the callback token
     * @param checkoutRequestID the CheckoutRequestID from the callback
     * @param resultCode        0 = success, non-zero = failure
     * @param resultDesc        description of the result
     * @param amount            the amount paid (for validation)
     * @param phoneNumber       the payer's phone number
     * @param mpesaReceipt      the Mpesa receipt number (on success)
     * @param receivedAt        when the callback was received
     */
    @Transactional
    public void processStkResult(Long schoolId, String checkoutRequestID,
                                  int resultCode, String resultDesc,
                                  BigDecimal amount, String phoneNumber,
                                  String mpesaReceipt, Instant receivedAt) {

        // 1. Save to durable inbox first
        CallbackInbox inbox = CallbackInbox.builder()
                .schoolId(schoolId)
                .channel(CallbackChannel.STK)
                .providerId(checkoutRequestID)
                .payerPhoneMasked(maskPhone(phoneNumber))
                .receivedAt(receivedAt)
                .minimalPayload(buildMinimalPayload(checkoutRequestID, resultCode, resultDesc, amount))
                .build();
        inbox = inboxRepository.save(inbox);

        try {
            // 2. Find the PENDING payment by CheckoutRequestID
            Optional<FeePayment> paymentOpt = feePaymentRepository
                    .findBySchoolIdAndCheckoutRequestID(schoolId, checkoutRequestID);

            if (paymentOpt.isEmpty()) {
                inbox.setProcessed(true);
                inbox.setProcessingResult("No payment found for checkoutRequestID: " + checkoutRequestID);
                inboxRepository.save(inbox);
                log.warn("STK callback: no payment found for checkoutRequestID={}", checkoutRequestID);
                return;
            }

            FeePayment payment = paymentOpt.get();

            // 3. Idempotency check — if already processed, skip
            if (payment.getStatus() != FeePaymentStatus.PENDING) {
                inbox.setProcessed(true);
                inbox.setProcessingResult("Idempotent skip: payment already " + payment.getStatus());
                inboxRepository.save(inbox);
                log.info("STK callback idempotent skip: paymentId={}, status={}",
                        payment.getId(), payment.getStatus());
                return;
            }

            // 4. Validate amount matches
            if (amount != null && payment.getAmount().compareTo(amount) != 0) {
                // Amount mismatch — hold for review
                payment.setStatus(FeePaymentStatus.HELD);
                payment.setFailureReason("Amount mismatch: expected " + payment.getAmount() + ", got " + amount);
                feePaymentRepository.save(payment);
                inbox.setProcessed(true);
                inbox.setProcessingResult("Amount mismatch — payment HELD");
                inboxRepository.save(inbox);
                log.warn("STK callback amount mismatch: paymentId={}, expected={}, got={}",
                        payment.getId(), payment.getAmount(), amount);
                return;
            }

            if (resultCode == 0) {
                // 5. Success — post the payment
                processSuccessfulPayment(inbox, payment, mpesaReceipt, receivedAt);
            } else {
                // 6. Failure — mark as FAILED
                payment.setStatus(FeePaymentStatus.FAILED);
                payment.setFailureReason(resultDesc);
                feePaymentRepository.save(payment);
                inbox.setProcessed(true);
                inbox.setProcessingResult("Failed: " + resultDesc);
                inboxRepository.save(inbox);
                log.info("STK callback failed: paymentId={}, reason={}", payment.getId(), resultDesc);
            }
        } catch (Exception e) {
            log.error("STK callback processing error", e);
            inbox.setProcessed(false);
            inbox.setProcessingResult("Error: " + e.getMessage());
            inbox.setRetryCount(inbox.getRetryCount() + 1);
            inboxRepository.save(inbox);
        }
    }

    /**
     * Process a successful STK payment: update status, post to ledger, allocate to invoices.
     */
    private void processSuccessfulPayment(CallbackInbox inbox, FeePayment payment,
                                          String mpesaReceipt, Instant receivedAt) {
        Long schoolId = payment.getSchoolId();
        Long studentId = payment.getStudentId();

        // Update payment
        payment.setStatus(FeePaymentStatus.SUCCEEDED);
        payment.setProviderReceipt(mpesaReceipt);
        payment.setConfirmedAt(receivedAt);
        payment = feePaymentRepository.save(payment);

        // Ensure fee account exists
        StudentFeeAccount account = studentFeeAccountRepository
                .findBySchoolIdAndStudentId(schoolId, studentId)
                .orElseGet(() -> studentFeeAccountRepository.save(
                        StudentFeeAccount.builder()
                                .schoolId(schoolId)
                                .studentId(studentId)
                                .build()));

        // Allocate to open invoices
        paymentPostingService.allocatePaymentToInvoices(account, payment);

        // Update cached balance
        ledgerService.updateCachedBalance(account);
        studentFeeAccountRepository.save(account);

        inbox.setProcessed(true);
        inbox.setProcessingResult("Success: payment posted and allocated");
        inboxRepository.save(inbox);

        log.info("STK payment successful: paymentId={}, studentId={}, amount={}",
                payment.getId(), studentId, payment.getAmount());
    }

    /**
     * Resolve a stale PENDING payment by querying the Mpesa status.
     * Used by the timeout job.
     * <p>
     * Idempotent: if the payment is already SUCCEEDED (e.g., a late callback arrived first),
     * this method does nothing. The @Version field on FeePayment ensures that concurrent
     * status updates (timeout job vs late callback) are serialized — one wins, the other
     * gets an optimistic lock failure and should retry.
     * </p>
     */
    @Transactional
    public void resolveStalePayment(Long schoolId, FeePayment payment, String resultDesc) {
        if (payment.getStatus() == FeePaymentStatus.SUCCEEDED) {
            return; // Already resolved by callback
        }
        if (payment.getStatus() == FeePaymentStatus.TIMED_OUT) {
            return; // Already resolved by a previous timeout run
        }
        if (payment.getStatus() != FeePaymentStatus.PENDING) {
            return; // In another terminal state — skip
        }

        payment.setStatus(FeePaymentStatus.TIMED_OUT);
        payment.setFailureReason(resultDesc != null ? resultDesc : "STK Push timed out");
        feePaymentRepository.save(payment);

        log.info("STK payment timed out: paymentId={}, studentId={}",
                payment.getId(), payment.getStudentId());
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() <= 4) return phone;
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }

    private String buildMinimalPayload(String checkoutRequestID, int resultCode,
                                        String resultDesc, BigDecimal amount) {
        return "{\"checkoutRequestID\":\"" + checkoutRequestID +
                "\",\"resultCode\":" + resultCode +
                ",\"resultDesc\":\"" + escapeJson(resultDesc) +
                "\",\"amount\":\"" + (amount != null ? amount.toPlainString() : "null") +
                "\"}";
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
