package com.peterscode.edutrack.lms.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Version;

import java.time.LocalDateTime;

@Entity
@Table(name = "lms_resource")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class LmsResource extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ResourceType type;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(length = 2048)
    private String url;

    @Column(nullable = false)
    private Boolean published;

    @Column(name = "visible_from")
    private LocalDateTime visibleFrom;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Version
    private Long version;
}
