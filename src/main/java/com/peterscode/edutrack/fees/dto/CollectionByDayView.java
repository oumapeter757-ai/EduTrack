package com.peterscode.edutrack.fees.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Collection report summary grouped by day.
 */
public record CollectionByDayView(
        LocalDate date,
        BigDecimal totalCollected,
        int paymentCount,
        String currency
) {}
