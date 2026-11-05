package com.peterscode.edutrack.notification.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface NotificationOutboxStore {

    long enqueue(EnqueueCommand command);

    List<DeliveryWorkItem> claimDue(int batchSize, Instant now, Instant leaseUntil);

    void markSending(long messageId, int attempts);

    void markSent(long messageId, int attempts, String providerMessageId, Instant sentAt);

    void markSkipped(long messageId, int attempts, String reason, Instant completedAt);

    void markFailed(long messageId, int attempts, NotificationMessageStatus status, String errorCode);

    void scheduleRetry(long messageId, int attempts, Instant nextAttemptAt, String errorCode);

    enum NotificationChannel {
        EMAIL,
        SMS
    }

    record EnqueueCommand(
            Long schoolId,
            String eventType,
            String eventKey,
            String payloadJson,
            List<EnqueueMessage> messages) {
    }

    record EnqueueMessage(
            String recipientType,
            Long recipientId,
            NotificationChannel channel,
            String destinationMasked,
            String destinationEncrypted,
            String language,
            String subject,
            String bodyRendered,
            boolean html,
            String category,
            Integer segments,
            BigDecimal estimatedCost,
            Instant scheduledFor,
            int smsQuotaLimit) {
        public EnqueueMessage(String recipientType, Long recipientId, NotificationChannel channel,
                              String destinationMasked, String destinationEncrypted, String language,
                              String subject, String bodyRendered, boolean html, String category,
                              Integer segments, BigDecimal estimatedCost, Instant scheduledFor) {
            this(recipientType, recipientId, channel, destinationMasked, destinationEncrypted, language,
                    subject, bodyRendered, html, category, segments, estimatedCost, scheduledFor, 0);
        }
    }

    record DeliveryWorkItem(
            long messageId,
            long schoolId,
            String recipientType,
            long recipientId,
            NotificationChannel channel,
            String category,
            String destinationEncrypted,
            String destinationMasked,
            String subject,
            String bodyRendered,
            boolean html,
            int attempts,
            String idempotencyKey) {
    }
}
