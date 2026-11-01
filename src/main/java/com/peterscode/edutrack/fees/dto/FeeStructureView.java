package com.peterscode.edutrack.fees.dto;

import com.peterscode.edutrack.fees.enums.FeeStructureStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response view for a fee structure.
 */
public record FeeStructureView(
        Long id,
        Long schoolId,
        Long termId,
        Long classLevelId,
        String name,
        FeeStructureStatus status,
        List<FeeItemView> items,
        BigDecimal totalAmount,
        LocalDateTime createdAt
) {
    public record FeeItemView(
            Long id,
            String name,
            BigDecimal amount,
            boolean mandatory
    ) {}
}
