package com.peterscode.edutrack.library.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "library_borrower_accounts")
@Setter
@Getter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class LibraryBorrowerAccount extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "borrower_type", nullable = false, length = 20)
    private String borrowerType;

    @Column(name = "borrower_id", nullable = false)
    private Long borrowerId;

    @Column(name = "active_loan_count", nullable = false)
    private Integer activeLoanCount;

    @Version
    @Column(nullable = false)
    private Long version;
}
