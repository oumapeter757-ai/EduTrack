package com.peterscode.edutrack.fees.dto;

import com.peterscode.edutrack.fees.enums.FeeInvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Response view for a fee invoice.
 */
public record FeeInvoiceView(
        Long id,
        String invoiceNumber,
        Long studentId,
        String studentName,
        Long termId,
        BigDecimal total,
        BigDecimal paid,
        BigDecimal balance,
        String currency,
        FeeInvoiceStatus status,
        LocalDate issuedOn,
        LocalDate dueOn,
        List<FeeInvoiceLineView> lines
) {
    public record FeeInvoiceLineView(
            String name,
            BigDecimal amount
    ) {}
}
