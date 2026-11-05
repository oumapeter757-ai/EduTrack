package com.peterscode.edutrack.notification.service;

import com.peterscode.edutrack.notification.entity.NotificationRecipientType;
import com.peterscode.edutrack.notification.service.NotificationOptOutTokenService.OptOutIdentity;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.NotificationChannel;
import com.peterscode.edutrack.notification.service.NotificationPreferenceStore.PreferenceIdentity;
import com.peterscode.edutrack.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceStore preferences;
    private final NotificationIdentityResolver identities;
    private final NotificationOptOutTokenService tokens;
    private final NotificationAuditStore audit;

    public NotificationPreferenceService(NotificationPreferenceStore preferences,
                                         NotificationIdentityResolver identities,
                                         NotificationOptOutTokenService tokens,
                                         NotificationAuditStore audit) {
        this.preferences = preferences;
        this.identities = identities;
        this.tokens = tokens;
        this.audit = audit;
    }

    public Map<NotificationChannel, Boolean> getOptionalPreferences(UserPrincipal principal) {
        PreferenceIdentity identity = identities.resolve(principal);
        return Map.of(
                NotificationChannel.EMAIL, preferences.isEnabled(principal.getSchoolId(),
                        identity.recipientType(), identity.recipientId(), NotificationChannel.EMAIL),
                NotificationChannel.SMS, preferences.isEnabled(principal.getSchoolId(),
                        identity.recipientType(), identity.recipientId(), NotificationChannel.SMS));
    }

    @Transactional
    public void updateOptionalPreference(UserPrincipal principal, NotificationChannel channel, boolean enabled) {
        if (channel == null) {
            throw new IllegalArgumentException("Notification channel is required");
        }
        PreferenceIdentity identity = identities.resolve(principal);
        preferences.setEnabled(principal.getSchoolId(), identity.recipientType(),
                identity.recipientId(), channel, enabled);
        audit.record(principal.getSchoolId(), principal.getId(),
                enabled ? "PREFERENCE_ENABLED" : "PREFERENCE_DISABLED",
                identity.recipientType().name(), identity.recipientId());
    }

    @Transactional
    public void optOut(String token) {
        OptOutIdentity identity = tokens.verify(token);
        preferences.setEnabled(identity.schoolId(), identity.recipientType(), identity.recipientId(),
                identity.channel(), false);
        audit.record(identity.schoolId(), null, "SIGNED_OPT_OUT",
                identity.recipientType().name(), identity.recipientId());
    }

    public String createOptOutToken(long schoolId, NotificationRecipientType recipientType,
                                    long recipientId, NotificationChannel channel) {
        return tokens.create(schoolId, recipientType, recipientId, channel);
    }
}
