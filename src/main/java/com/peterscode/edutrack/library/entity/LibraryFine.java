package com.peterscode.edutrack.library.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "library_fines")
@Setter
@Getter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class LibraryFine extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "borrower_type", nullable = false, length = 20)
    private String borrowerType;

    @Column(name = "borrower_id", nullable = false)
    private Long borrowerId;

    @Column(nullable = false, length = 20)
    private String reason;

    @Column(name = "overdue_days")
    private Integer overdueDays;

    @Column(name = "suggested_amount", precision = 10, scale = 2)
    private BigDecimal suggestedAmount;

    @Column(precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "assessed_by")
    private Long assessedBy;

    @Column(name = "assessed_at")
    private Instant assessedAt;

    @Column(name = "paid_reference", length = 200)
    private String paidReference;

    @Column(name = "waived_reason", length = 500)
    private String waivedReason;

    @Version
    @Column(nullable = false)
    private Long version;
}
