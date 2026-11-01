package com.peterscode.edutrack.fees.service;

import com.peterscode.edutrack.fees.entity.FeePayment;
import com.peterscode.edutrack.fees.enums.FeePaymentStatus;
import com.peterscode.edutrack.fees.mpesa.MpesaClient;
import com.peterscode.edutrack.fees.repository.FeePaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Scheduled job that resolves stale PENDING STK payments.
 * <p>
 * Runs every 5 minutes. For each payment that has been PENDING longer than
 * the configured timeout, it queries the Mpesa status and updates the payment.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StkPushTimeoutJob {

    private final FeePaymentRepository feePaymentRepository;
    private final MpesaClient mpesaClient;
    private final StkCallbackService stkCallbackService;
    private final Clock clock;

    private static final int TIMEOUT_MINUTES = 5;

    /**
     * Run every 5 minutes to check for stale PENDING payments.
     */
    @Scheduled(fixedRate = 300000)
    @Transactional
    public void processStalePayments() {
        Instant threshold = Instant.now(clock).minusSeconds(TIMEOUT_MINUTES * 60L);
        List<FeePayment> stalePayments = feePaymentRepository
                .findByStatusAndCreatedAtBefore(FeePaymentStatus.PENDING, threshold);

        if (stalePayments.isEmpty()) {
            return;
        }

        log.info("STK timeout job: found {} stale payments", stalePayments.size());

        for (FeePayment payment : stalePayments) {
            try {
                if (payment.getCheckoutRequestID() != null && !payment.getCheckoutRequestID().isBlank()) {
                    // Query Mpesa for the actual status
                    MpesaClient.StkPushStatusResult status = mpesaClient
                            .queryStkPushStatus(payment.getCheckoutRequestID());

                    if (status.completed()) {
                        if ("0".equals(status.resultCode())) {
                            // Payment succeeded but callback was missed — this should have been
                            // handled by the callback. If we're here, the callback was lost.
                            // Mark as TIMED_OUT for manual review rather than auto-posting.
                            log.warn("STK payment succeeded but callback was missed: paymentId={}",
                                    payment.getId());
                        }
                        stkCallbackService.resolveStalePayment(
                                payment.getSchoolId(), payment, status.resultDesc());
                    } else {
                        // Still pending with Mpesa — leave it
                        log.debug("STK payment still pending with Mpesa: paymentId={}",
                                payment.getId());
                    }
                } else {
                    // No CheckoutRequestID — payment was never successfully initiated
                    stkCallbackService.resolveStalePayment(
                            payment.getSchoolId(), payment, "No CheckoutRequestID");
                }
            } catch (Exception e) {
                log.error("Error processing stale payment: paymentId={}", payment.getId(), e);
            }
        }
    }
}
