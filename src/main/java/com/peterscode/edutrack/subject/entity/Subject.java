package com.peterscode.edutrack.subject.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
        name = "subjects",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_subject_school_code", columnNames = {"school_id", "code"})
        },
        indexes = {
                @Index(name = "idx_subject_school", columnList = "school_id"),
                @Index(name = "idx_subject_school_department", columnList = "school_id, department"),
                @Index(name = "idx_subject_class_level", columnList = "class_level_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Subject extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "class_level_id")
    private Long classLevelId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "credit_hours")
    private Integer creditHours;

    @JsonProperty("isElective")
    @Column(name = "is_elective", nullable = false)
    @Builder.Default
    private boolean isElective = false;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    public boolean isElective() {
        return isElective;
    }

    public boolean getIsElective() {
        return isElective;
    }

    public void setIsElective(boolean isElective) {
        this.isElective = isElective;
    }

    public void setElective(boolean isElective) {
        this.isElective = isElective;
    }
}
