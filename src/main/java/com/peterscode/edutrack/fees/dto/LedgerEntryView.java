package com.peterscode.edutrack.fees.dto;

import com.peterscode.edutrack.fees.enums.LedgerEntryType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response view for a ledger entry (statement line).
 */
public record LedgerEntryView(
        Long id,
        LedgerEntryType type,
        BigDecimal amount,
        String description,
        Instant occurredAt
) {}
