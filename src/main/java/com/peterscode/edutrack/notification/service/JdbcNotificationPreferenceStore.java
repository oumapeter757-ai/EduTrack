package com.peterscode.edutrack.notification.service;

import com.peterscode.edutrack.notification.entity.NotificationCategory;
import com.peterscode.edutrack.notification.entity.NotificationRecipientType;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.NotificationChannel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcNotificationPreferenceStore implements NotificationPreferenceStore {

    private final JdbcTemplate jdbc;

    public JdbcNotificationPreferenceStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isEnabled(long schoolId, NotificationRecipientType recipientType, long recipientId,
                             NotificationChannel channel) {
        return jdbc.query("""
                SELECT enabled FROM notification_preferences
                WHERE school_id = ? AND recipient_type = ? AND recipient_id = ?
                  AND channel = ? AND category = 'OPTIONAL'
                """, rs -> rs.next() ? rs.getBoolean(1) : true,
                schoolId, recipientType.name(), recipientId, channel.name());
    }

    @Override
    @Transactional
    public void setEnabled(long schoolId, NotificationRecipientType recipientType, long recipientId,
                           NotificationChannel channel, boolean enabled) {
        if (recipientType == null || recipientId < 1 || channel == null) {
            throw new IllegalArgumentException("Notification preference identity is invalid");
        }
        jdbc.update("""
                INSERT INTO notification_preferences
                    (school_id, recipient_type, recipient_id, channel, category, enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE enabled = VALUES(enabled), updated_at = UTC_TIMESTAMP(6)
                """, schoolId, recipientType.name(), recipientId, channel.name(),
                NotificationCategory.OPTIONAL.name(), enabled);
    }
}
