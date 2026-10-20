package com.peterscode.edutrack.student.audit;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_audit")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StudentAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="student_id", nullable=false) private Long studentId;
    @Column(name="school_id", nullable=false) private Long schoolId;
    @Column(nullable=false, length=40) private String action;
    @Column(name="actor_id") private Long actorId;
    @Column(name="old_value", columnDefinition="TEXT") private String oldValue;
    @Column(name="new_value", columnDefinition="TEXT") private String newValue;
    @Column(name="occurred_at", nullable=false) private LocalDateTime occurredAt;
}
