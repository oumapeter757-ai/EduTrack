package com.peterscode.edutrack.notification.service;

import com.peterscode.edutrack.notification.entity.NotificationRecipientType;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.NotificationChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class NotificationOptOutTokenService {

    private static final String HMAC = "HmacSHA256";
    private static final Duration TOKEN_LIFETIME = Duration.ofDays(30);
    private final byte[] signingKey;
    private final Clock clock;

    public NotificationOptOutTokenService(
            @Value("${edutrack.notifications.opt-out-signing-key:}") String signingKey,
            Clock clock) {
        this.signingKey = signingKey == null ? new byte[0] : signingKey.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public String create(long schoolId, NotificationRecipientType recipientType, long recipientId,
                         NotificationChannel channel) {
        if (schoolId < 1 || recipientId < 1 || recipientType == null || channel == null) {
            throw new IllegalArgumentException("Opt-out token identity is invalid");
        }
        long expiresAt = clock.instant().plus(TOKEN_LIFETIME).getEpochSecond();
        String payload = schoolId + ":" + recipientType.name() + ":" + recipientId + ":"
                + channel.name() + ":" + expiresAt;
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sign(encodedPayload));
    }

    public boolean isConfigured() {
        return signingKey.length >= 32;
    }

    public OptOutIdentity verify(String token) {
        if (token == null || token.length() > 1024) {
            throw new IllegalArgumentException("Opt-out token is invalid");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Opt-out token is invalid");
        }
        byte[] expected = sign(parts[0]);
        byte[] supplied;
        try {
            supplied = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Opt-out token is invalid");
        }
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new IllegalArgumentException("Opt-out token is invalid");
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String[] fields = decoded.split(":", -1);
            if (fields.length != 5) {
                throw new IllegalArgumentException("Opt-out token is invalid");
            }
            long schoolId = Long.parseLong(fields[0]);
            NotificationRecipientType recipientType = NotificationRecipientType.valueOf(fields[1]);
            long recipientId = Long.parseLong(fields[2]);
            NotificationChannel channel = NotificationChannel.valueOf(fields[3]);
            long expiresAt = Long.parseLong(fields[4]);
            if (schoolId < 1 || recipientId < 1 || expiresAt <= clock.instant().getEpochSecond()) {
                throw new IllegalArgumentException("Opt-out token has expired or is invalid");
            }
            return new OptOutIdentity(schoolId, recipientType, recipientId, channel);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Opt-out token has expired or is invalid");
        }
    }

    private byte[] sign(String value) {
        if (signingKey.length < 32) {
            throw new IllegalStateException("Notification opt-out signing key must contain at least 32 characters");
        }
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(signingKey, HMAC));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign notification opt-out token");
        }
    }

    public record OptOutIdentity(long schoolId, NotificationRecipientType recipientType,
                                 long recipientId, NotificationChannel channel) {
    }
}
