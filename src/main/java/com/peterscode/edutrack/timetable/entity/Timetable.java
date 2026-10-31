package com.peterscode.edutrack.timetable.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "timetables", uniqueConstraints = @UniqueConstraint(name = "uk_timetable_school_term", columnNames = {"school_id", "term_id"}),
        indexes = @Index(name = "idx_timetable_school", columnList = "school_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Timetable extends BaseEntity {
    @Column(name = "school_id", nullable = false)
    private Long schoolId;
    @Column(name = "term_id", nullable = false)
    private Long termId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TimetableStatus status = TimetableStatus.DRAFT;
    @Column(name = "published_at")
    private LocalDateTime publishedAt;
    @Column(name = "published_by")
    private Long publishedBy;
    @Version
    @Column(nullable = false)
    private Long version;

    public enum TimetableStatus { DRAFT, PUBLISHED }
}
