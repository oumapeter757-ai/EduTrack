package com.peterscode.edutrack.notification.entity;

import com.peterscode.edutrack.notification.service.NotificationMessageStatus;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.NotificationChannel;
import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification_messages",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_message_recipient_channel",
                columnNames = {"request_id", "recipient_type", "recipient_id", "channel"}),
        indexes = {
                @Index(name = "idx_notification_message_school_queue",
                        columnList = "school_id, status, next_attempt_at, scheduled_for"),
                @Index(name = "idx_notification_message_school_recipient",
                        columnList = "school_id, recipient_type, recipient_id"),
                @Index(name = "idx_notification_message_provider",
                        columnList = "provider, provider_message_id")
        })
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class NotificationMessageEntity extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, length = 16)
    private NotificationRecipientType recipientType;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 8)
    private NotificationChannel channel;

    @Column(name = "destination_masked", nullable = false, length = 254)
    private String destinationMasked;

    @Column(name = "destination_encrypted", nullable = false, length = 2048)
    private String destinationEncrypted;

    @Column(name = "language", nullable = false, length = 8)
    private String language;

    @Column(name = "subject", length = 500)
    private String subject;

    @Column(name = "body_rendered", nullable = false, columnDefinition = "TEXT")
    private String bodyRendered;

    @Column(name = "body_html", nullable = false)
    private boolean html;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 16)
    private NotificationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private NotificationMessageStatus status;

    @Column(name = "skip_reason", length = 64)
    private String skipReason;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "provider", length = 32)
    private String provider;

    @Column(name = "provider_message_id", length = 190)
    private String providerMessageId;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "claimed_until")
    private LocalDateTime claimedUntil;

    @Column(name = "segments", nullable = false)
    private Integer segments;

    @Column(name = "estimated_cost", precision = 12, scale = 4)
    private BigDecimal estimatedCost;

    @Column(name = "scheduled_for")
    private LocalDateTime scheduledFor;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
}
