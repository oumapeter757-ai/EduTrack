package com.peterscode.edutrack.payment.dto;

import com.peterscode.edutrack.payment.enums.BillingPeriod;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPriceDto {

    private Long id;
    private SubscriptionTier tier;
    private BillingPeriod billingPeriod;
    private BigDecimal amount;
    private String currency;
    private boolean active;
    private LocalDate effectiveFrom;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
