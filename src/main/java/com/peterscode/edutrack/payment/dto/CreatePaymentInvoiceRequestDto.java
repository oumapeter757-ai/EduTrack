package com.peterscode.edutrack.payment.dto;

import com.peterscode.edutrack.payment.enums.BillingPeriod;
import com.peterscode.edutrack.payment.enums.PaymentInvoiceStatus;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentInvoiceRequestDto {

    @NotNull
    private SubscriptionTier tier;

    @NotNull
    private BillingPeriod billingPeriod;
}
