package com.peterscode.edutrack.lms.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Version;

@Entity
@Table(name = "school_storage_usage")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class SchoolStorageUsage extends BaseEntity {

    @Column(name = "school_id", nullable = false, unique = true)
    private Long schoolId;

    @Column(name = "used_bytes", nullable = false)
    private Long usedBytes;

    @Version
    private Long version;
}
