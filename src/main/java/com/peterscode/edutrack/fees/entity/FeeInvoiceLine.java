package com.peterscode.edutrack.fees.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * A snapshot line on a fee invoice. Once the invoice is created, these lines never change
 * even if the underlying fee structure changes.
 */
@Entity
@Table(
        name = "fee_invoice_lines",
        indexes = {
                @Index(name = "idx_fee_invoice_line_invoice", columnList = "invoice_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class FeeInvoiceLine extends BaseEntity {

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false, insertable = false, updatable = false)
    private FeeInvoice invoice;
}
