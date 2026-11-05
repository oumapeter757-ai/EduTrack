package com.peterscode.edutrack.enrollment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record EnrollmentSettingsRequest(
        @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal suggestedMinAverage,
        boolean blockOnUnpaidFees,
        boolean blockOnOpenLibraryLoans,
        boolean requireClosedTerm,
        @Min(0) @Max(365) int undoWindowDays,
        @Min(0) @Max(3650) int alumniLoginDays,
        @Min(1) @Max(500) int batchSize) {
}
