package com.peterscode.edutrack.payment.entity;

import com.peterscode.edutrack.payment.enums.BillingPeriod;
import com.peterscode.edutrack.payment.enums.PaymentInvoiceStatus;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "payment_invoices", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_invoice_number", columnNames = "invoiceNumber")
})
public class PaymentInvoice extends BaseEntity {

    @Column(name = "invoice_number", unique = true, nullable = false, length = 64)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionTier tier;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", nullable = false)
    private BillingPeriod billingPeriod;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentInvoiceStatus status;

    @Column(name = "mpesa_transaction_id", length = 64)
    private String mpesaTransactionId;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
