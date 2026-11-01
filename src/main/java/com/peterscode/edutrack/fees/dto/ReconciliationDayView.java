package com.peterscode.edutrack.fees.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Daily reconciliation report comparing provider-confirmed payments with ledger entries.
 */
public record ReconciliationDayView(
        LocalDate date,
        BigDecimal providerConfirmedAmount,
        BigDecimal ledgerPostedAmount,
        BigDecimal difference,
        int providerPaymentCount,
        int ledgerPaymentCount,
        String currency
) {}
