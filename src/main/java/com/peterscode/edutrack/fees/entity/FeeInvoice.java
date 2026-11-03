package com.peterscode.edutrack.fees.entity;

import com.peterscode.edutrack.fees.enums.FeeInvoiceStatus;
import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "fee_invoices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_fee_invoice_school_number",
                        columnNames = {"school_id", "invoice_number"}),
                @UniqueConstraint(name = "uk_fee_invoice_student_term_structure",
                        columnNames = {"school_id", "student_id", "term_id", "structure_id"})
        },
        indexes = {
                @Index(name = "idx_fee_invoice_school", columnList = "school_id"),
                @Index(name = "idx_fee_invoice_student", columnList = "student_id"),
                @Index(name = "idx_fee_invoice_status", columnList = "status"),
                @Index(name = "idx_fee_invoice_term", columnList = "term_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class FeeInvoice extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "term_id", nullable = false)
    private Long termId;

    @Column(name = "structure_id", nullable = false)
    private Long structureId;

    @Column(name = "invoice_number", nullable = false, length = 50)
    private String invoiceNumber;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal paid = BigDecimal.ZERO;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "KES";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FeeInvoiceStatus status = FeeInvoiceStatus.OPEN;

    @Column(name = "issued_on", nullable = false)
    private LocalDate issuedOn;

    @Column(name = "due_on")
    private LocalDate dueOn;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<FeeInvoiceLine> lines = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;
}
