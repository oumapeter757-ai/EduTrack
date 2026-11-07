package com.peterscode.edutrack.fees.service;

import com.peterscode.edutrack.fees.entity.FeePayment;
import com.peterscode.edutrack.fees.enums.FeePaymentStatus;
import com.peterscode.edutrack.fees.mpesa.MpesaClient;
import com.peterscode.edutrack.fees.repository.FeePaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Scheduled job that resolves stale PENDING STK payments.
 * <p>
 * Runs every 5 minutes. Uses SKIP LOCKED to claim a bounded batch of stale payments
 * so multiple instances process disjoint rows. Each payment is processed independently;
 * external Mpesa calls happen outside any DB transaction.
 * </p>
 * <p>
 * Multi-instance safety:
 * 1. Claim bounded batch with PESSIMISTIC_WRITE + SKIP LOCKED
 * 2. Set claimed_by and claimed_until lease on each row
 * 3. Process each payment in its own short transaction
 * 4. Expired leases are returned to the queue before the next claim
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

    @Value("${edutrack.stk-timeout.minutes:5}")
    private int timeoutMinutes;

    @Value("${edutrack.stk-timeout.batch-size:50}")
    private int batchSize;

    @Value("${edutrack.stk-timeout.lease-minutes:10}")
    private int leaseMinutes;

    /**
     * Unique identifier for this job instance (set at startup).
     */
    private static final String RUNNER_ID = "stk-timeout-" + UUID.randomUUID().toString().substring(0, 8);

    /**
     * Run every 5 minutes to check for stale PENDING payments.
     * Uses SKIP LOCKED so multiple instances claim disjoint rows.
     */
    @Scheduled(fixedRate = 300000)
    public void processStalePayments() {
        LocalDateTime now = LocalDateTime.now(clock)
                .atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
        LocalDateTime threshold = now.minusMinutes(timeoutMinutes);

        // Return expired claims back to the queue first
        int returned = returnExpiredClaims(now);
        if (returned > 0) {
            log.info("STK timeout job: returned {} expired claims", returned);
        }

        // Claim a bounded batch of stale payments with SKIP LOCKED
        List<FeePayment> claimed;
        try {
            claimed = claimStalePayments(FeePaymentStatus.PENDING, threshold, now);
        } catch (Exception e) {
            log.error("STK timeout job: failed to claim payments", e);
            return;
        }

        if (claimed.isEmpty()) {
            return;
        }

        log.info("STK timeout job: claimed {} stale payments (runner={})", claimed.size(), RUNNER_ID);

        // Process each payment independently — no single transaction for all payments.
        // External Mpesa calls are made OUTSIDE any DB transaction.
        for (FeePayment payment : claimed) {
            processOneStalePayment(payment);
        }
    }

    /**
     * Return expired claims back to the queue so another runner can pick them up.
     */
    @Transactional
    public int returnExpiredClaims(LocalDateTime now) {
        return feePaymentRepository.returnExpiredClaims(now, FeePaymentStatus.PENDING);
    }

    /**
     * Claim a bounded batch of stale PENDING payments using SKIP LOCKED.
     * Multiple instances calling this method will get disjoint sets of rows.
     */
    @Transactional
    public List<FeePayment> claimStalePayments(FeePaymentStatus status, LocalDateTime before, LocalDateTime now) {
        LocalDateTime leaseUntil = now.plusMinutes(leaseMinutes);
        feePaymentRepository.claimStalePaymentsAtomically(status.name(), before, now,
                RUNNER_ID, leaseUntil, batchSize);
        return feePaymentRepository.findClaimedByRunner(RUNNER_ID, leaseUntil);
    }

    /**
     * Mark claimed rows with this runner's lease identifier and expiry.
     */
    @Transactional
    public void markClaimed(List<FeePayment> payments, String runnerId, LocalDateTime leaseUntil) {
        for (FeePayment p : payments) {
            p.setClaimedBy(runnerId);
            p.setClaimedUntil(leaseUntil);
            feePaymentRepository.save(p);
        }
    }

    /**
     * Process a single stale payment.
     * The Mpesa query happens outside a transaction; only the status update is transactional.
     */
    private void processOneStalePayment(FeePayment payment) {
        try {
            String checkoutRequestId = payment.getCheckoutRequestID();
            MpesaClient.StkPushStatusResult status = null;

            // Query Mpesa OUTSIDE any DB transaction
            if (checkoutRequestId != null && !checkoutRequestId.isBlank()) {
                status = mpesaClient.queryStkPushStatus(checkoutRequestId);

                if (status != null && status.completed()) {
                    if ("0".equals(status.resultCode())) {
                        log.warn("STK payment succeeded but callback was missed: paymentId={}",
                                payment.getId());
                    }
                } else {
                    // Still pending with Mpesa — leave it, clear claim for next run
                    log.debug("STK payment still pending with Mpesa: paymentId={}",
                            payment.getId());
                    clearClaim(payment.getId());
                    return;
                }
            }

            // Update payment status in its own short transaction
            // Need to reload the entity to get the latest version
            FeePayment fresh = feePaymentRepository.findById(payment.getId()).orElse(null);
            if (fresh != null) {
                String reason = (status != null) ? status.resultDesc() : "No CheckoutRequestID";
                stkCallbackService.resolveStalePayment(payment.getSchoolId(), fresh, reason);
            }
        } catch (Exception e) {
            log.error("Error processing stale payment: paymentId={}", payment.getId(), e);
        }
    }

    /**
     * Clear the claim marker so the row can be picked up again next run.
     */
    @Transactional
    public void clearClaim(Long paymentId) {
        feePaymentRepository.findById(paymentId).ifPresent(p -> {
            p.setClaimedBy(null);
            p.setClaimedUntil(null);
            feePaymentRepository.save(p);
        });
    }
}
