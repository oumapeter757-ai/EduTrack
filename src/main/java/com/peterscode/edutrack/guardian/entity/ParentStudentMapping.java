package com.peterscode.edutrack.guardian.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "parent_student_mappings",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_parent_student_mapping",
        columnNames = {"school_id", "parent_id", "student_id"}),
    indexes = {
        @Index(name = "idx_parent_mapping_school", columnList = "school_id"),
        @Index(name = "idx_parent_mapping_parent", columnList = "parent_id"),
        @Index(name = "idx_parent_mapping_student", columnList = "student_id"),
        @Index(name = "idx_parent_mapping_school_parent_active", columnList = "school_id, parent_id, active"),
        @Index(name = "idx_parent_mapping_school_student_active", columnList = "school_id, student_id, active")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ParentStudentMapping extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "parent_id", nullable = false)
    private Long parentId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(length = 50)
    private String relationship;

    @Column(name = "primary_flag", nullable = false)
    @Builder.Default
    private boolean primaryFlag = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "linked_by", nullable = false)
    private Long linkedBy;

    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt;

    @Column(name = "unlinked_at")
    private LocalDateTime unlinkedAt;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;
}
