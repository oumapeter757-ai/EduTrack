package com.peterscode.edutrack.fees.entity;

import com.peterscode.edutrack.fees.enums.FeePaymentChannel;
import com.peterscode.edutrack.fees.enums.FeePaymentStatus;
import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A fee payment record. Idempotent by (school_id, channel, provider_receipt).
 */
@Entity
@Table(
        name = "fee_payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_fee_payment_school_provider",
                        columnNames = {"school_id", "channel", "provider_receipt"}),
                @UniqueConstraint(name = "uk_fee_payment_school_receipt",
                        columnNames = {"school_id", "receipt_number"})
        },
        indexes = {
                @Index(name = "idx_fee_payment_school", columnList = "school_id"),
                @Index(name = "idx_fee_payment_student", columnList = "student_id"),
                @Index(name = "idx_fee_payment_status", columnList = "status"),
                @Index(name = "idx_fee_payment_provider", columnList = "provider_receipt")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class FeePayment extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "student_id")
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeePaymentChannel channel;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "KES";

    @Column(name = "provider_receipt", length = 100)
    private String providerReceipt;

    @Column(name = "checkout_request_id", length = 100)
    private String checkoutRequestID;

    @Column(name = "payer_phone_masked", length = 20)
    private String payerPhoneMasked;

    @Column(name = "account_reference", length = 100)
    private String accountReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FeePaymentStatus status = FeePaymentStatus.PENDING;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "initiated_by")
    private Long initiatedBy;

    @Column(name = "receipt_number", length = 50)
    private String receiptNumber;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Version
    @Builder.Default
    private Integer version = 0;
}
