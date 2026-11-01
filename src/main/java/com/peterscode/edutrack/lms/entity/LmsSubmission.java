package com.peterscode.edutrack.lms.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "lms_submission")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class LmsSubmission extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(nullable = false)
    private Boolean late;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubmissionStatus status;

    @Column(precision = 10, scale = 2)
    private BigDecimal score;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(nullable = false)
    private Boolean released;

    @Column(name = "marked_by")
    private Long markedBy;

    @Column(name = "marked_at")
    private LocalDateTime markedAt;

    @Version
    private Long version;
}
