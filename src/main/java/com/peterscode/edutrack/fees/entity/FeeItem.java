package com.peterscode.edutrack.fees.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(
        name = "fee_items",
        indexes = {
                @Index(name = "idx_fee_item_structure", columnList = "structure_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class FeeItem extends BaseEntity {

    @Column(name = "structure_id", nullable = false)
    private Long structureId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    @Builder.Default
    private boolean mandatory = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "structure_id", nullable = false, insertable = false, updatable = false)
    private FeeStructure structure;
}
