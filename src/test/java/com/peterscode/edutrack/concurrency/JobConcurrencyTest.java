package com.peterscode.edutrack.concurrency;

import com.peterscode.edutrack.fees.entity.FeePayment;
import com.peterscode.edutrack.fees.enums.FeePaymentStatus;
import com.peterscode.edutrack.fees.repository.FeePaymentRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency tests for scheduled jobs.
 * These tests verify that two job runners may process the same rows
 * without SKIP LOCKED protection — demonstrating the hazard.
 */
@Tag("concurrency")
class JobConcurrencyTest extends ConcurrencyTestBase {

    @Autowired
    private FeePaymentRepository feePaymentRepository;

    /**
     * Test: Two runners query stale PENDING payments simultaneously.
     * Without SKIP LOCKED, both runners see the same rows and may process them twice.
     * This test demonstrates the hazard — it will show both runners finding the same payments.
     */
    @Test
    void twoRunners_withoutSkipLocked_mayProcessSameRows() throws Exception {
        // Create 10 stale PENDING payments
        for (int i = 0; i < 10; i++) {
            FeePayment payment = FeePayment.builder()
                    .schoolId(schoolId)
                    .channel(com.peterscode.edutrack.fees.enums.FeePaymentChannel.STK)
                    .amount(new BigDecimal("100.00"))
                    .currency("KES")
                    .status(FeePaymentStatus.PENDING)
                    .build();
            feePaymentRepository.save(payment);
        }

        // Both runners query the same stale payments
        // Without SKIP LOCKED, they both see all 10 rows
        List<FeePayment> runner1View = feePaymentRepository
                .findByStatusAndCreatedAtBefore(FeePaymentStatus.PENDING, LocalDateTime.now());
        List<FeePayment> runner2View = feePaymentRepository
                .findByStatusAndCreatedAtBefore(FeePaymentStatus.PENDING, LocalDateTime.now());

        // Both runners see the same rows — this is the hazard
        assertThat(runner1View.size())
                .as("Runner 1 sees all pending payments")
                .isGreaterThanOrEqualTo(10);
        assertThat(runner2View.size())
                .as("Runner 2 sees the same pending payments — double processing risk")
                .isGreaterThanOrEqualTo(10);
    }
}
