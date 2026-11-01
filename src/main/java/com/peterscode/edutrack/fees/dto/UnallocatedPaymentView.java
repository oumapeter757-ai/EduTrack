package com.peterscode.edutrack.fees.dto;

import com.peterscode.edutrack.fees.enums.FeePaymentChannel;
import com.peterscode.edutrack.fees.enums.UnallocatedPaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Response view for an unallocated payment.
 */
public record UnallocatedPaymentView(
        Long id,
        BigDecimal amount,
        String currency,
        FeePaymentChannel channel,
        String providerReceipt,
        String accountReference,
        String payerPhoneMasked,
        UnallocatedPaymentStatus status,
        String reason,
        Long matchedStudentId,
        Instant resolvedAt,
        LocalDateTime createdAt
) {}
