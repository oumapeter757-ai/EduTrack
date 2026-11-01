package com.peterscode.edutrack.fees.dto;

import com.peterscode.edutrack.fees.enums.FeePaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO for STK Push initiation.
 */
public record StkPushResponseDto(
        Long paymentId,
        FeePaymentStatus status,
        BigDecimal amount,
        String currency,
        String checkoutRequestID,
        String customerMessage,
        Instant createdAt
) {}
