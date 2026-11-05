package com.peterscode.edutrack.notification.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class NotificationAuditStore {

    private final JdbcTemplate jdbc;

    public NotificationAuditStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void record(long schoolId, Long actorUserId, String action, String recipientType, Long recipientId) {
        if (schoolId < 1 || action == null || !action.matches("[A-Z_]{1,48}")) {
            throw new IllegalArgumentException("Notification audit entry is invalid");
        }
        jdbc.update("""
                INSERT INTO notification_audit
                    (school_id, actor_user_id, action, recipient_type, recipient_id, created_at)
                VALUES (?, ?, ?, ?, ?, UTC_TIMESTAMP(6))
                """, schoolId, actorUserId, action, recipientType, recipientId);
    }
}
