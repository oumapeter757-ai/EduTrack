package com.peterscode.edutrack.fees.entity;

import com.peterscode.edutrack.fees.enums.FeePaymentChannel;
import com.peterscode.edutrack.fees.enums.UnallocatedPaymentStatus;
import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payment that arrived via C2B but could not be auto-matched to a student.
 * Requires manual resolution by ACCOUNTANT or SCHOOL_ADMIN.
 */
@Entity
@Table(
        name = "unallocated_payments",
        indexes = {
                @Index(name = "idx_unallocated_school", columnList = "school_id"),
                @Index(name = "idx_unallocated_status", columnList = "status"),
                @Index(name = "idx_unallocated_provider", columnList = "provider_receipt")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class UnallocatedPayment extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "KES";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeePaymentChannel channel;

    @Column(name = "provider_receipt", length = 100)
    private String providerReceipt;

    @Column(name = "account_reference", length = 100)
    private String accountReference;

    @Column(name = "payer_phone_masked", length = 20)
    private String payerPhoneMasked;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UnallocatedPaymentStatus status = UnallocatedPaymentStatus.PENDING;

    @Column(name = "matched_student_id")
    private Long matchedStudentId;

    @Column(name = "matched_at")
    private Instant matchedAt;

    @Column(length = 500)
    private String reason;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
