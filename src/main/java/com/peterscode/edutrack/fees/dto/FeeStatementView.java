package com.peterscode.edutrack.fees.dto;

import com.peterscode.edutrack.fees.enums.FeeInvoiceStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response view for a student fee statement.
 */
public record FeeStatementView(
        Long studentId,
        String studentName,
        String currency,
        BigDecimal balance,
        List<LedgerEntryView> entries
) {}
