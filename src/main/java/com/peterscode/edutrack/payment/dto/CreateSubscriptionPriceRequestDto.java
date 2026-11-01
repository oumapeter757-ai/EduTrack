package com.peterscode.edutrack.payment.dto;

import com.peterscode.edutrack.payment.enums.BillingPeriod;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSubscriptionPriceRequestDto {

    @NotNull
    private SubscriptionTier tier;

    @NotNull
    private BillingPeriod billingPeriod;

    @NotNull
    @Positive
    private BigDecimal amount;

    private String currency;

    private LocalDate effectiveFrom;
}
