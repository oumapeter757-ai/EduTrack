package com.peterscode.edutrack.fees.entity;

import com.peterscode.edutrack.fees.enums.StudentFeeAccountStatus;
import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(
        name = "student_fee_accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_fee_account_school_student",
                        columnNames = {"school_id", "student_id"})
        },
        indexes = {
                @Index(name = "idx_fee_account_school", columnList = "school_id"),
                @Index(name = "idx_fee_account_student", columnList = "student_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class StudentFeeAccount extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "KES";

    @Column(name = "cached_balance", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal cachedBalance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StudentFeeAccountStatus status = StudentFeeAccountStatus.ACTIVE;

    @Version
    @Builder.Default
    private Integer version = 0;
}
