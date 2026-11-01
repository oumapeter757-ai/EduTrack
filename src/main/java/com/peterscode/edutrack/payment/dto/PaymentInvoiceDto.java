package com.peterscode.edutrack.payment.dto;

import com.peterscode.edutrack.payment.enums.BillingPeriod;
import com.peterscode.edutrack.payment.enums.PaymentInvoiceStatus;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInvoiceDto {

    private Long id;
    private String invoiceNumber;
    private Long schoolId;
    private String schoolName;
    private SubscriptionTier tier;
    private BillingPeriod billingPeriod;
    private BigDecimal amount;
    private String currency;
    private PaymentInvoiceStatus status;
    private String mpesaTransactionId;
    private LocalDateTime validatedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
