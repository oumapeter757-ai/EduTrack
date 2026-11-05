package com.peterscode.edutrack.notification.service;

import com.peterscode.edutrack.notification.entity.NotificationRecipientType;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.NotificationChannel;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationOptOutTokenServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void tokenIsSignedAndContainsOnlyNotificationIdentity() {
        NotificationOptOutTokenService service = service();
        String token = service.create(7, NotificationRecipientType.PARENT, 12, NotificationChannel.SMS);

        assertThat(service.verify(token)).isEqualTo(new NotificationOptOutTokenService.OptOutIdentity(
                7, NotificationRecipientType.PARENT, 12, NotificationChannel.SMS));
        assertThat(new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[0]),
                java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("@", "+");
    }

    @Test
    void alteredSignatureIsRejected() {
        NotificationOptOutTokenService service = service();
        String token = service.create(7, NotificationRecipientType.PARENT, 12, NotificationChannel.EMAIL);
        String altered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

        assertThatThrownBy(() -> service.verify(altered))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private NotificationOptOutTokenService service() {
        return new NotificationOptOutTokenService("0123456789abcdef0123456789abcdef", CLOCK);
    }
}
