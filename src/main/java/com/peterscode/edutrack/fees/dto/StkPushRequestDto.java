package com.peterscode.edutrack.fees.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * Request DTO for initiating an STK Push payment.
 */
public record StkPushRequestDto(
        @NotNull(message = "Student ID is required")
        Long studentId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        /**
         * Optional phone number. If not provided, defaults to the payer's profile phone.
         * Must be 12 digits, Kenyan format (e.g., 254712345678).
         */
        @Pattern(regexp = "^254[0-9]{9}$", message = "Phone must be 12 digits starting with 254")
        String phone
) {}
