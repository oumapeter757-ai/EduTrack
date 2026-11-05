package com.peterscode.edutrack.notification.service;

import com.peterscode.edutrack.notification.service.NotificationOutboxStore.DeliveryWorkItem;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.EnqueueCommand;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.EnqueueMessage;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.NotificationChannel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
public class JdbcNotificationOutboxStore implements NotificationOutboxStore {

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final NotificationSmsQuotaProperties smsQuotaProperties;

    @Autowired
    public JdbcNotificationOutboxStore(JdbcTemplate jdbc, Clock clock,
                                       NotificationSmsQuotaProperties smsQuotaProperties) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.smsQuotaProperties = smsQuotaProperties;
    }

    @Override
    @Transactional
    public long enqueue(EnqueueCommand command) {
        jdbc.update("""
                INSERT INTO notification_requests
                    (school_id, event_type, event_key, payload, status, created_at, updated_at)
                VALUES (?, ?, ?, CAST(? AS JSON), 'PENDING', ?, ?)
                ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
                """, command.schoolId(), command.eventType(), command.eventKey(), command.payloadJson(),
                utcNow(), utcNow());
        Long requestId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (requestId == null || requestId < 1) {
            throw new IllegalStateException("Unable to persist notification request");
        }
        jdbc.queryForObject("""
                SELECT id FROM notification_requests
                WHERE school_id = ? AND id = ? FOR UPDATE
                """, Long.class, command.schoolId(), requestId);

        for (EnqueueMessage message : command.messages()) {
            Integer existing = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM notification_messages
                    WHERE request_id = ? AND recipient_type = ? AND recipient_id = ? AND channel = ?
                    """, Integer.class, requestId, message.recipientType(),
                    message.recipientId(), message.channel().name());
            if (existing != null && existing > 0) {
                continue;
            }
            boolean quotaReserved = false;
            boolean quotaExceeded = false;
            if (message.channel() == NotificationChannel.SMS) {
                int quotaLimit = message.smsQuotaLimit() > 0
                        ? Math.min(message.smsQuotaLimit(), 10_000_000) : 0;
                quotaReserved = reserveSmsQuota(command.schoolId(),
                        message.segments() == null ? 0 : message.segments(), quotaLimit);
                quotaExceeded = !quotaReserved;
            }
            String status = quotaExceeded ? "SUPPRESSED"
                    : message.scheduledFor() == null ? "PENDING" : "SCHEDULED";
            jdbc.update("""
                    INSERT INTO notification_messages
                        (school_id, request_id, recipient_type, recipient_id, channel,
                         destination_masked, destination_encrypted, language, subject, body_rendered,
                         body_html, category, status, attempts, segments, estimated_cost, scheduled_for,
                         created_at, updated_at, version, sms_quota_reserved, failure_code, skip_reason)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, 0, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE id = id
                    """, command.schoolId(), requestId, message.recipientType(), message.recipientId(),
                    message.channel().name(), message.destinationMasked(), message.destinationEncrypted(),
                    message.language(), message.subject(), message.bodyRendered(), message.html(), message.category(),
                    status,
                    message.segments() == null ? 0 : message.segments(), message.estimatedCost(),
                    timestamp(message.scheduledFor()), utcNow(), utcNow(), quotaReserved,
                    quotaExceeded ? "SMS_QUOTA_EXCEEDED" : null,
                    quotaExceeded ? "SMS_QUOTA_EXCEEDED" : null);
        }
        return requestId;
    }

    private boolean reserveSmsQuota(long schoolId, int segments, int limit) {
        if (segments < 1 || limit < 1) {
            return false;
        }
        LocalDate billingMonth = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).withDayOfMonth(1);
        jdbc.update("""
                INSERT IGNORE INTO notification_sms_usage
                    (school_id, billing_month, reserved_segments, sent_segments)
                VALUES (?, ?, 0, 0)
                """, schoolId, billingMonth);
        return jdbc.update("""
                UPDATE notification_sms_usage
                SET reserved_segments = reserved_segments + ?
                WHERE school_id = ? AND billing_month = ?
                  AND reserved_segments + sent_segments + ? <= ?
                """, segments, schoolId, billingMonth, segments, limit) == 1;
    }

    @Override
    @Transactional
    public List<DeliveryWorkItem> claimDue(int batchSize, Instant now, Instant leaseUntil) {
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException("Batch size is outside the permitted range");
        }
        jdbc.update("""
                UPDATE notification_messages
                SET status = 'DEAD', failure_code = 'OUTCOME_UNKNOWN', claimed_until = NULL,
                    updated_at = ?, version = version + 1
                WHERE status = 'SENDING' AND claimed_until <= ?
                """, timestamp(now), timestamp(now));
        List<Long> cursorValues = jdbc.query("""
                SELECT last_school_id FROM notification_worker_cursor
                WHERE id = 1 FOR UPDATE SKIP LOCKED
                """, (rs, rowNum) -> {
            long value = rs.getLong("last_school_id");
            return rs.wasNull() ? null : value;
        });
        if (cursorValues.isEmpty()) {
            return List.of();
        }
        Long lastSchoolId = cursorValues.getFirst();
        java.util.ArrayList<DeliveryWorkItem> claimed = new java.util.ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            List<DeliveryWorkItem> due = jdbc.query("""
                    SELECT id, school_id, recipient_type, recipient_id, channel, category,
                           destination_encrypted, destination_masked, subject, body_rendered,
                           body_html, attempts, request_id
                    FROM notification_messages
                    WHERE ((status IN ('PENDING', 'SCHEDULED') AND (next_attempt_at IS NULL OR next_attempt_at <= ?))
                        OR (status = 'CLAIMED' AND claimed_until <= ?))
                      AND (scheduled_for IS NULL OR scheduled_for <= ?)
                    ORDER BY CASE WHEN school_id > ? THEN 0 ELSE 1 END,
                             school_id, COALESCE(next_attempt_at, scheduled_for, created_at), id
                    LIMIT 1 FOR UPDATE SKIP LOCKED
                    """, (rs, rowNum) -> new DeliveryWorkItem(
                    rs.getLong("id"),
                    rs.getLong("school_id"),
                    rs.getString("recipient_type"),
                    rs.getLong("recipient_id"),
                    NotificationChannel.valueOf(rs.getString("channel")),
                    rs.getString("category"),
                    rs.getString("destination_encrypted"),
                    rs.getString("destination_masked"),
                    rs.getString("subject"),
                    rs.getString("body_rendered"),
                    rs.getBoolean("body_html"),
                    rs.getInt("attempts") + 1,
                    rs.getString("request_id") + ":" + rs.getLong("id")
            ), timestamp(now), timestamp(now), timestamp(now), lastSchoolId == null ? 0L : lastSchoolId);
            if (due.isEmpty()) {
                break;
            }
            DeliveryWorkItem item = due.getFirst();
            int updated = jdbc.update("""
                    UPDATE notification_messages
                    SET status = 'CLAIMED', claimed_until = ?, attempts = attempts + 1,
                        next_attempt_at = NULL, updated_at = ?,
                        version = version + 1
                    WHERE id = ?
                    """, timestamp(leaseUntil), timestamp(now), item.messageId());
            if (updated != 1) {
                throw new IllegalStateException("Notification claim was lost");
            }
            lastSchoolId = item.schoolId();
            jdbc.update("UPDATE notification_worker_cursor SET last_school_id = ? WHERE id = 1", lastSchoolId);
            claimed.add(item);
        }
        return List.copyOf(claimed);
    }

    @Override
    @Transactional
    public void markSending(long messageId, int attempts) {
        int updated = jdbc.update("""
                UPDATE notification_messages
                SET status = 'SENDING', updated_at = ?, version = version + 1
                WHERE id = ? AND status = 'CLAIMED' AND attempts = ?
                """, utcNow(), messageId, attempts);
        if (updated != 1) {
            throw new IllegalStateException("Notification claim expired before provider dispatch");
        }
    }

    @Override
    @Transactional
    public void markSent(long messageId, int attempts, String providerMessageId, Instant sentAt) {
        if (providerMessageId != null && providerMessageId.length() > 190) {
            throw new IllegalArgumentException("Provider message id is invalid");
        }
        updateInDelivery(messageId, "SENDING", attempts, """
                SET status = 'SENT', provider_message_id = ?, sent_at = ?, claimed_until = NULL,
                    skip_reason = NULL, updated_at = ?, version = version + 1
                """, providerMessageId, timestamp(sentAt), timestamp(sentAt));
        settleSmsReservation(messageId, true);
    }

    @Override
    @Transactional
    public void markSkipped(long messageId, int attempts, String reason, Instant completedAt) {
        updateInDelivery(messageId, "CLAIMED", attempts, """
                SET status = 'SKIPPED', skip_reason = ?, claimed_until = NULL,
                    updated_at = ?, version = version + 1
                """, reason, timestamp(completedAt));
        settleSmsReservation(messageId, false);
    }

    @Override
    @Transactional
    public void markFailed(long messageId, int attempts, NotificationMessageStatus status, String errorCode) {
        if (status != NotificationMessageStatus.FAILED && status != NotificationMessageStatus.DEAD) {
            throw new IllegalArgumentException("Final failure status is invalid");
        }
        updateInDelivery(messageId, "CLAIMED,SENDING", attempts, """
                SET status = ?, failure_code = ?, claimed_until = NULL,
                    updated_at = ?, version = version + 1
                """, status.name(), safeCode(errorCode), utcNow());
        if (status == NotificationMessageStatus.FAILED) {
            settleSmsReservation(messageId, false);
        }
    }

    private void settleSmsReservation(long messageId, boolean sent) {
        List<SmsReservation> reservations = jdbc.query("""
                SELECT school_id, DATE_FORMAT(created_at, '%Y-%m-01') AS billing_month, segments
                FROM notification_messages
                WHERE id = ? AND channel = 'SMS' AND sms_quota_reserved = TRUE
                FOR UPDATE
                """, (rs, rowNum) -> new SmsReservation(rs.getLong("school_id"),
                java.sql.Date.valueOf(rs.getString("billing_month")).toLocalDate(), rs.getInt("segments")), messageId);
        if (reservations.isEmpty()) {
            return;
        }
        SmsReservation reservation = reservations.getFirst();
        int updated = jdbc.update("""
                UPDATE notification_sms_usage
                SET reserved_segments = reserved_segments - ?,
                    sent_segments = sent_segments + ?
                WHERE school_id = ? AND billing_month = ? AND reserved_segments >= ?
                """, reservation.segments(), sent ? reservation.segments() : 0,
                reservation.schoolId(), reservation.billingMonth(), reservation.segments());
        if (updated != 1) {
            throw new IllegalStateException("Notification SMS quota reservation is inconsistent");
        }
        jdbc.update("UPDATE notification_messages SET sms_quota_reserved = FALSE WHERE id = ?", messageId);
    }

    private record SmsReservation(long schoolId, java.time.LocalDate billingMonth, int segments) {
    }

    @Override
    @Transactional
    public void scheduleRetry(long messageId, int attempts, Instant nextAttemptAt, String errorCode) {
        updateInDelivery(messageId, "SENDING", attempts, """
                SET status = 'PENDING', next_attempt_at = ?, failure_code = ?, claimed_until = NULL,
                    updated_at = ?, version = version + 1
                """, timestamp(nextAttemptAt), safeCode(errorCode), utcNow());
    }

    private void updateInDelivery(long messageId, String expectedStates, int attempts,
                                  String setClause, Object... parameters) {
        Object[] args = new Object[parameters.length + 2];
        System.arraycopy(parameters, 0, args, 0, parameters.length);
        args[parameters.length] = messageId;
        args[parameters.length + 1] = attempts;
        int updated = jdbc.update("UPDATE notification_messages " + setClause + " WHERE id = ? AND status IN ("
                + expectedStates.replace("CLAIMED", "'CLAIMED'").replace("SENDING", "'SENDING'")
                + ") AND attempts = ?", args);
        if (updated != 1) {
            throw new IllegalStateException("Notification delivery state changed before update");
        }
    }

    private String safeCode(String errorCode) {
        if (errorCode == null || !errorCode.matches("[A-Z0-9_]{1,64}")) {
            return "DELIVERY_FAILURE";
        }
        return errorCode;
    }

    private LocalDateTime timestamp(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
