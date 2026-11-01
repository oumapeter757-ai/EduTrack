package com.peterscode.edutrack.fees.entity;

import com.peterscode.edutrack.fees.enums.LedgerEntryType;
import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Append-only ledger entry. No update or delete methods are allowed after save.
 * A mistake is corrected by a reversing entry with a mandatory reason.
 * <p>
 * Amount convention: positive for charges (money owed), negative for credits (money received).
 * </p>
 */
@Entity
@Table(
        name = "ledger_entries",
        indexes = {
                @Index(name = "idx_ledger_school_account", columnList = "school_id, account_id"),
                @Index(name = "idx_ledger_school_created", columnList = "school_id, created_at"),
                @Index(name = "idx_ledger_invoice", columnList = "invoice_id"),
                @Index(name = "idx_ledger_payment", columnList = "payment_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class LedgerEntry extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerEntryType type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "reversal_of_entry_id")
    private Long reversalOfEntryId;

    @Column(length = 500)
    private String description;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
