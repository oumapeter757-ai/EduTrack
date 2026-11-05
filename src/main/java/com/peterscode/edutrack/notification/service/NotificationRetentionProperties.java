package com.peterscode.edutrack.notification.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "edutrack.notifications.retention")
public class NotificationRetentionProperties {

    private int messageDays = 365;
    private int auditDays = 730;
    private int batchSize = 1000;

    public int getMessageDays() {
        return messageDays;
    }

    public void setMessageDays(int messageDays) {
        this.messageDays = messageDays;
    }

    public int getAuditDays() {
        return auditDays;
    }

    public void setAuditDays(int auditDays) {
        this.auditDays = auditDays;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
