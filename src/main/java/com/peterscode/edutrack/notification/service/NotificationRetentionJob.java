package com.peterscode.edutrack.notification.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationRetentionJob {

    private final JdbcTemplate jdbc;
    private final NotificationRetentionProperties properties;

    public NotificationRetentionJob(JdbcTemplate jdbc, NotificationRetentionProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Scheduled(cron = "${edutrack.notifications.retention.cleanup-cron:0 25 3 * * *}")
    @Transactional
    public void purgeExpiredRows() {
        validateProperties();
        jdbc.update("""
                DELETE FROM notification_requests
                WHERE created_at < DATE_SUB(UTC_TIMESTAMP(6), INTERVAL ? DAY)
                ORDER BY created_at
                LIMIT ?
                """, properties.getMessageDays(), properties.getBatchSize());
        jdbc.update("""
                DELETE FROM notification_audit
                WHERE created_at < DATE_SUB(UTC_TIMESTAMP(6), INTERVAL ? DAY)
                ORDER BY created_at
                LIMIT ?
                """, properties.getAuditDays(), properties.getBatchSize());
        jdbc.update("""
                DELETE FROM notification_sms_usage
                WHERE billing_month < DATE_FORMAT(DATE_SUB(UTC_DATE(), INTERVAL 12 MONTH), '%Y-%m-01')
                LIMIT ?
                """, properties.getBatchSize());
    }

    private void validateProperties() {
        if (properties.getMessageDays() < 30 || properties.getAuditDays() < 30
                || properties.getBatchSize() < 1 || properties.getBatchSize() > 10_000) {
            throw new IllegalStateException("Notification retention settings are outside permitted bounds");
        }
    }
}
