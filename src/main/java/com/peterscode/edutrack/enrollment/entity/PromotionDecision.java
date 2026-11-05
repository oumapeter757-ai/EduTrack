package com.peterscode.edutrack.enrollment.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.time.LocalDateTime;

@Entity
@Table(name = "promotion_decisions", indexes = {
        @Index(name = "idx_promotion_decision_run_status", columnList = "run_id, status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class PromotionDecision extends BaseEntity {
    @Column(name = "run_id", nullable = false) private Long runId;
    @Column(name = "student_id", nullable = false) private Long studentId;
    @Column(name = "from_enrollment_id") private Long fromEnrollmentId;
    @Column(name = "from_enrollment_version") private Long fromEnrollmentVersion;
    @Enumerated(EnumType.STRING) @Column(name = "proposed_outcome", nullable = false, length = 30) private PromotionOutcome proposedOutcome;
    @Enumerated(EnumType.STRING) @Column(name = "final_outcome", length = 30) private PromotionOutcome finalOutcome;
    @Column(name = "target_class_level_id") private Long targetClassLevelId;
    @Column(name = "target_stream_id") private Long targetStreamId;
    @Column(name = "override_reason", length = 500) private String overrideReason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private PromotionDecisionStatus status;
    @Column(name = "warnings_json", columnDefinition = "json") private String warningsJson;
    @Column(name = "to_enrollment_id") private Long toEnrollmentId;
    @Column(name = "applied_at") private LocalDateTime appliedAt;
    @Column(name = "claimed_until") private LocalDateTime claimedUntil;
    @Column(name = "claimed_by", length = 100) private String claimedBy;
    @Version @Column(nullable = false) private Long version;
}
