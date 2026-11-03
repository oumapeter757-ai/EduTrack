package com.peterscode.edutrack.library.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "borrowing_policies")
@Setter
@Getter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class BorrowingPolicy extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "borrower_type", nullable = false, length = 20)
    private String borrowerType;

    @Column(name = "max_loans", nullable = false)
    private Integer maxLoans;

    @Column(name = "loan_days", nullable = false)
    private Integer loanDays;

    @Column(name = "max_renewals", nullable = false)
    private Integer maxRenewals;

    @Column(name = "suggested_fine_per_day", nullable = false, precision = 10, scale = 2)
    private BigDecimal suggestedFinePerDay;

    @Column(name = "max_unpaid_fines", nullable = false, precision = 10, scale = 2)
    private BigDecimal maxUnpaidFines;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;
}
