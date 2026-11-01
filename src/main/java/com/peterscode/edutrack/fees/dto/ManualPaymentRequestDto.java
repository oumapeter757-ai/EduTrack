package com.peterscode.edutrack.fees.dto;

import com.peterscode.edutrack.fees.enums.FeePaymentChannel;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request to record a manual payment.
 */
public record ManualPaymentRequestDto(
        @NotNull Long studentId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull FeePaymentChannel channel,
        String payerPhone,
        String accountReference
) {}
