package com.peterscode.edutrack.enrollment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "enrollment_audit", indexes = {
        @Index(name = "idx_enrollment_audit_school_entity", columnList = "school_id, entity_type, entity_id, occurred_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EnrollmentAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "school_id", nullable = false) private Long schoolId;
    @Column(name = "entity_type", nullable = false, length = 50) private String entityType;
    @Column(name = "entity_id", nullable = false) private Long entityId;
    @Column(nullable = false, length = 50) private String action;
    @Column(name = "actor_id") private Long actorId;
    @Column(name = "old_value", length = 1000) private String oldValue;
    @Column(name = "new_value", length = 1000) private String newValue;
    @Column(length = 500) private String reason;
    @Column(name = "occurred_at", nullable = false) private LocalDateTime occurredAt;
}
