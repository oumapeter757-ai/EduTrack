package com.peterscode.edutrack.fees.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request to create a fee structure.
 */
public record CreateFeeStructureRequestDto(
        @NotNull Long termId,
        @NotNull Long classLevelId,
        @NotNull @Size(min = 1, max = 100) String name,
        @NotNull @Size(min = 1) CreateFeeItemRequestDto[] items
) {
    public record CreateFeeItemRequestDto(
            @NotNull @Size(min = 1, max = 100) String name,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            boolean mandatory
    ) {}
}
