package com.peterscode.edutrack.fees.entity;

import com.peterscode.edutrack.fees.enums.FeeStructureStatus;
import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "fee_structures",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_fee_structure_school_term_class",
                        columnNames = {"school_id", "term_id", "class_level_id", "name"})
        },
        indexes = {
                @Index(name = "idx_fee_structure_school", columnList = "school_id"),
                @Index(name = "idx_fee_structure_term", columnList = "term_id"),
                @Index(name = "idx_fee_structure_class_level", columnList = "class_level_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class FeeStructure extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;

    @Column(name = "term_id", nullable = false)
    private Long termId;

    @Column(name = "class_level_id", nullable = false)
    private Long classLevelId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FeeStructureStatus status = FeeStructureStatus.DRAFT;

    @OneToMany(mappedBy = "structure", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<FeeItem> items = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;
}
