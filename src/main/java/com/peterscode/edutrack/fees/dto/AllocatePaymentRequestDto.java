package com.peterscode.edutrack.fees.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request to allocate an unallocated payment to a student.
 */
public record AllocatePaymentRequestDto(
        @NotNull Long studentId,
        String reason
) {}
