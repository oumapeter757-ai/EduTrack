package com.peterscode.edutrack.notification.service;

public enum NotificationMessageStatus {
    PENDING,
    SCHEDULED,
    CLAIMED,
    SENDING,
    SENT,
    DELIVERED,
    FAILED,
    DEAD,
    SKIPPED,
    SUPPRESSED
}
