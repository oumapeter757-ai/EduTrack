package com.peterscode.edutrack.notification.service;

import com.peterscode.edutrack.notification.entity.NotificationCategory;
import com.peterscode.edutrack.notification.entity.NotificationRecipientType;
import com.peterscode.edutrack.notification.provider.EmailProvider;
import com.peterscode.edutrack.notification.provider.SmsProvider;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.EnqueueMessage;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.NotificationChannel;
import com.peterscode.edutrack.shared.PhoneNumberNormalizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class NotificationMessageComposer {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final DestinationCipher cipher;
    private final NotificationPreferenceStore preferences;
    private final NotificationOptOutTokenService optOutTokens;
    private final NotificationSmsQuotaService smsQuota;
    private final EmailProvider emailProvider;
    private final SmsProvider smsProvider;
    private final String publicBaseUrl;

    public NotificationMessageComposer(DestinationCipher cipher,
                                       NotificationPreferenceStore preferences,
                                       NotificationOptOutTokenService optOutTokens,
                                       NotificationSmsQuotaService smsQuota,
                                       EmailProvider emailProvider,
                                       SmsProvider smsProvider,
                                       @Value("${edutrack.notifications.public-base-url:}") String publicBaseUrl) {
        this.cipher = cipher;
        this.preferences = preferences;
        this.optOutTokens = optOutTokens;
        this.smsQuota = smsQuota;
        this.emailProvider = emailProvider;
        this.smsProvider = smsProvider;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
    }

    public List<EnqueueMessage> compose(long schoolId, List<Recipient> recipients, String subject,
                                        String body, NotificationCategory category) {
        if (schoolId < 1 || recipients == null || subject == null || body == null || category == null) {
            throw new IllegalArgumentException("Notification composition request is invalid");
        }
        if (category == NotificationCategory.OPTIONAL
                && (!optOutTokens.isConfigured() || publicBaseUrl.isBlank())) {
            return List.of();
        }
        if (!cipher.isConfigured()) {
            return List.of();
        }
        int smsQuotaLimit = smsProvider.isEnabled() ? smsQuota.limitFor(schoolId) : 0;
        List<EnqueueMessage> messages = new ArrayList<>();
        for (Recipient recipient : recipients) {
            if (recipient == null || recipient.recipientType() == null || recipient.recipientId() < 1) {
                continue;
            }
            if (emailProvider.isEnabled() && hasDestination(recipient.email())
                    && allowed(schoolId, recipient, NotificationChannel.EMAIL, category)) {
                String destination = recipient.email().trim();
                if (EMAIL.matcher(destination).matches()) {
                    String rendered = appendOptOut(body, schoolId, recipient, NotificationChannel.EMAIL, category);
                    messages.add(new EnqueueMessage(recipient.recipientType().name(), recipient.recipientId(),
                            NotificationChannel.EMAIL, maskEmail(destination), cipher.encrypt(destination),
                            "en", subject, rendered, false, category.name(), 0, null, null));
                }
            }
            if (smsProvider.isEnabled() && recipient.recipientType() == NotificationRecipientType.PARENT
                    && hasDestination(recipient.phone())
                    && allowed(schoolId, recipient, NotificationChannel.SMS, category)) {
                final String destination;
                try {
                    destination = PhoneNumberNormalizer.normalize(recipient.phone());
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                String rendered = appendOptOut(body, schoolId, recipient, NotificationChannel.SMS, category);
                int segments = estimateSmsSegments(rendered);
                messages.add(new EnqueueMessage(recipient.recipientType().name(), recipient.recipientId(),
                        NotificationChannel.SMS, maskPhone(destination), cipher.encrypt(destination),
                        "en", null, rendered, false, category.name(), segments, null, null,
                        smsQuotaLimit));
            }
        }
        return List.copyOf(messages);
    }

    public boolean isReadyFor(NotificationCategory category) {
        return cipher.isConfigured()
                && (emailProvider.isEnabled() || smsProvider.isEnabled())
                && (category != NotificationCategory.OPTIONAL
                    || (optOutTokens.isConfigured() && !publicBaseUrl.isBlank()));
    }

    private boolean allowed(long schoolId, Recipient recipient, NotificationChannel channel,
                            NotificationCategory category) {
        return category == NotificationCategory.ESSENTIAL
                || preferences.isEnabled(schoolId, recipient.recipientType(), recipient.recipientId(), channel);
    }

    private String appendOptOut(String body, long schoolId, Recipient recipient,
                                NotificationChannel channel, NotificationCategory category) {
        if (category != NotificationCategory.OPTIONAL) {
            return body;
        }
        String token = optOutTokens.create(schoolId, recipient.recipientType(), recipient.recipientId(), channel);
        String separator = publicBaseUrl.contains("?") ? "&" : "?";
        return body + (channel == NotificationChannel.EMAIL ? "\n\nUnsubscribe: " : " Opt out: ")
                + publicBaseUrl + "/api/v1/notifications/opt-out" + separator + "token=" + token;
    }

    private int estimateSmsSegments(String text) {
        int gsmUnits = gsmUnits(text);
        int units = gsmUnits >= 0 ? gsmUnits : text.length();
        int singleLimit = gsmUnits >= 0 ? 160 : 70;
        int multipartLimit = gsmUnits >= 0 ? 153 : 67;
        return units <= singleLimit ? 1 : (units + multipartLimit - 1) / multipartLimit;
    }

    private int gsmUnits(String text) {
        String basic = "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ\u001bÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡"
                + "ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà";
        String extended = "^{}\\\\[~]|€";
        int units = 0;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (basic.indexOf(character) >= 0) {
                units++;
            } else if (extended.indexOf(character) >= 0) {
                units += 2;
            } else {
                return -1;
            }
        }
        return units;
    }

    private boolean hasDestination(String destination) {
        return destination != null && !destination.isBlank();
    }

    private String maskEmail(String value) {
        int at = value.indexOf('@');
        if (at < 1) {
            return "***";
        }
        return value.substring(0, 1) + "***" + value.substring(at);
    }

    private String maskPhone(String value) {
        return "***" + value.substring(Math.max(0, value.length() - 4));
    }

    public record Recipient(NotificationRecipientType recipientType, long recipientId,
                            String email, String phone) {
    }
}
