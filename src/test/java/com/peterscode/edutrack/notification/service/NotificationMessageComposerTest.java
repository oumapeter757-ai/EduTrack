package com.peterscode.edutrack.notification.service;

import com.peterscode.edutrack.notification.entity.NotificationCategory;
import com.peterscode.edutrack.notification.entity.NotificationRecipientType;
import com.peterscode.edutrack.notification.provider.EmailProvider;
import com.peterscode.edutrack.notification.provider.ProviderReceipt;
import com.peterscode.edutrack.notification.provider.SmsProvider;
import com.peterscode.edutrack.notification.service.NotificationMessageComposer.Recipient;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.EnqueueMessage;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.NotificationChannel;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationMessageComposerTest {

    @Test
    void composesOptionalEmailForEligibleRecipientsAndSmsOnlyForParents() {
        DestinationCipher cipher = mock(DestinationCipher.class);
        NotificationPreferenceStore preferences = mock(NotificationPreferenceStore.class);
        NotificationSmsQuotaService smsQuota = mock(NotificationSmsQuotaService.class);
        when(cipher.isConfigured()).thenReturn(true);
        when(cipher.encrypt(any())).thenAnswer(invocation -> "encrypted:" + invocation.getArgument(0));
        when(preferences.isEnabled(any(Long.class), any(), any(Long.class), any())).thenReturn(true);
        when(smsQuota.limitFor(7)).thenReturn(100);

        NotificationMessageComposer composer = new NotificationMessageComposer(
                cipher, preferences,
                new NotificationOptOutTokenService(
                        "0123456789abcdef0123456789abcdef",
                        Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"), ZoneOffset.UTC)),
                smsQuota, emailProvider(), smsProvider(), "https://edutrack.example");

        List<EnqueueMessage> messages = composer.compose(7, List.of(
                        new Recipient(NotificationRecipientType.PARENT, 12, "parent@example.com", "+254712345678"),
                        new Recipient(NotificationRecipientType.STUDENT, 13, "student@example.com", "+254700000000")),
                "Attendance update", "An attendance update is available.", NotificationCategory.OPTIONAL);

        assertThat(messages).hasSize(3);
        assertThat(messages).filteredOn(message -> message.channel() == NotificationChannel.SMS)
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.recipientType()).isEqualTo("PARENT");
                    assertThat(message.destinationMasked()).endsWith("5678");
                    assertThat(message.smsQuotaLimit()).isEqualTo(100);
                    assertThat(message.bodyRendered()).contains("/api/v1/notifications/opt-out?token=");
                });
        assertThat(messages).filteredOn(message -> message.channel() == NotificationChannel.EMAIL)
                .allSatisfy(message -> assertThat(message.bodyRendered())
                        .contains("Unsubscribe: https://edutrack.example/api/v1/notifications/opt-out?token="));
    }

    private EmailProvider emailProvider() {
        return new EmailProvider() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public ProviderReceipt send(EmailDelivery delivery) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private SmsProvider smsProvider() {
        return new SmsProvider() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public ProviderReceipt send(SmsDelivery delivery) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
