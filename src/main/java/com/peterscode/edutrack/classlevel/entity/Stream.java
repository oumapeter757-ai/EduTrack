package com.peterscode.edutrack.classlevel.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
        name = "streams",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_stream_class_level_code", columnNames = {"class_level_id", "code"})
        },
        indexes = {
                @Index(name = "idx_stream_school", columnList = "school_id"),
                @Index(name = "idx_stream_class_level", columnList = "class_level_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Stream extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "class_level_id", nullable = false)
    private Long classLevelId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
