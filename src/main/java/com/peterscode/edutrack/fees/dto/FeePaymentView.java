package com.peterscode.edutrack.fees.dto;

import com.peterscode.edutrack.fees.enums.FeePaymentChannel;
import com.peterscode.edutrack.fees.enums.FeePaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Response view for a fee payment.
 */
public record FeePaymentView(
        Long id,
        Long studentId,
        String studentName,
        FeePaymentChannel channel,
        BigDecimal amount,
        String currency,
        String payerPhoneMasked,
        FeePaymentStatus status,
        String receiptNumber,
        Instant confirmedAt,
        LocalDateTime createdAt
) {}
