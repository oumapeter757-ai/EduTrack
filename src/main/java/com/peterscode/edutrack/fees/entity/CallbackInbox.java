package com.peterscode.edutrack.fees.entity;

import com.peterscode.edutrack.fees.enums.CallbackChannel;
import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Durable inbox for Mpesa callbacks. Every callback is saved here before processing.
 * Retained for 90 days by default. Phone numbers are masked.
 */
@Entity
@Table(
        name = "callback_inbox",
        indexes = {
                @Index(name = "idx_callback_inbox_school_channel", columnList = "school_id, channel"),
                @Index(name = "idx_callback_inbox_provider", columnList = "provider_id"),
                @Index(name = "idx_callback_inbox_processed", columnList = "processed, received_at"),
                @Index(name = "idx_callback_inbox_retention", columnList = "received_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CallbackInbox extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CallbackChannel channel;

    @Column(name = "provider_id", nullable = false, length = 100)
    private String providerId;

    @Column(name = "minimal_payload", columnDefinition = "JSON")
    private String minimalPayload;

    @Column(name = "payer_phone_masked", length = 20)
    private String payerPhoneMasked;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean processed = false;

    @Column(name = "processing_result", length = 500)
    private String processingResult;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;
}
