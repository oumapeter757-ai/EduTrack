package com.peterscode.edutrack.notification.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "notification_requests",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_notification_request_school_event_key",
                        columnNames = {"school_id", "event_key"}),
                @UniqueConstraint(name = "uk_notification_request_school_id",
                        columnNames = {"school_id", "id"})
        },
        indexes = {
                @Index(name = "idx_notification_request_school_status_created",
                        columnList = "school_id, status, created_at")
        })
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class NotificationRequestEntity extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "event_key", nullable = false, length = 190)
    private String eventKey;

    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payload;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
}
