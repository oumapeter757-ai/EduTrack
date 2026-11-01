package com.peterscode.edutrack.fees.dto;

import java.math.BigDecimal;

/**
 * Response view for a parent's linked child with fee summary.
 */
public record LinkedChildView(
        Long studentId,
        String studentName,
        String admissionNumber,
        BigDecimal balance,
        String currency
) {}
