package com.peterscode.edutrack.enrollment.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.time.LocalDateTime;

@Entity
@Table(name = "promotion_runs", indexes = {
        @Index(name = "idx_promotion_run_school_status", columnList = "school_id, status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class PromotionRun extends BaseEntity {
    @Column(name = "school_id", nullable = false) private Long schoolId;
    @Column(name = "from_academic_year_id", nullable = false) private Long fromAcademicYearId;
    @Column(name = "to_academic_year_id", nullable = false) private Long toAcademicYearId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private PromotionRunStatus status;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "approved_by") private Long approvedBy;
    @Column(name = "approved_at") private LocalDateTime approvedAt;
    @Column(name = "started_at") private LocalDateTime startedAt;
    @Column(name = "completed_at") private LocalDateTime completedAt;
    @Column(name = "lease_until") private LocalDateTime leaseUntil;
    @Column(name = "counts_json", columnDefinition = "json") private String countsJson;
    @Column(name = "open_marker") private Integer openMarker;
    @Version @Column(nullable = false) private Long version;
}
