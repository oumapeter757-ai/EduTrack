package com.peterscode.edutrack.notification.service;

import com.peterscode.edutrack.notification.entity.NotificationRecipientType;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.NotificationChannel;

public interface NotificationPreferenceStore {

    boolean isEnabled(long schoolId, NotificationRecipientType recipientType, long recipientId,
                      NotificationChannel channel);

    void setEnabled(long schoolId, NotificationRecipientType recipientType, long recipientId,
                    NotificationChannel channel, boolean enabled);

    record PreferenceIdentity(NotificationRecipientType recipientType, long recipientId) {
    }
}
