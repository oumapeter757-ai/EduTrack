package com.peterscode.edutrack.fees.dto;

import java.math.BigDecimal;

/**
 * Collection report summary grouped by channel (STK, PAYBILL, MANUAL).
 */
public record CollectionByChannelView(
        String channel,
        BigDecimal totalCollected,
        int paymentCount,
        String currency
) {}
