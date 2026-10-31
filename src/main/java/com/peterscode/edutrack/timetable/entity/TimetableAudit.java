package com.peterscode.edutrack.timetable.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "timetable_audit", indexes = @Index(name = "idx_timetable_audit_school_entity", columnList = "school_id, entity_type, entity_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TimetableAudit extends BaseEntity {
    @Column(name = "school_id", nullable = false)
    private Long schoolId;
    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;
    @Column(name = "entity_id", nullable = false)
    private Long entityId;
    @Column(nullable = false, length = 40)
    private String action;
    @Column(name = "actor_id")
    private Long actorId;
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
