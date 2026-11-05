package com.peterscode.edutrack.notification.service;

import com.peterscode.edutrack.notification.provider.DeliveryFailureException;
import com.peterscode.edutrack.notification.provider.EmailProvider;
import com.peterscode.edutrack.notification.provider.ProviderReceipt;
import com.peterscode.edutrack.notification.provider.SmsProvider;
import com.peterscode.edutrack.notification.entity.NotificationCategory;
import com.peterscode.edutrack.notification.entity.NotificationRecipientType;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.DeliveryWorkItem;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
public class NotificationDeliveryWorker {

    private final NotificationOutboxStore store;
    private final EmailProvider emailProvider;
    private final SmsProvider smsProvider;
    private final DestinationCipher destinationCipher;
    private final NotificationRetryPolicy retryPolicy;
    private final NotificationPreferenceStore preferences;
    private final Clock clock;
    private final NotificationWorkerProperties properties;

    public NotificationDeliveryWorker(
            NotificationOutboxStore store,
            EmailProvider emailProvider,
            SmsProvider smsProvider,
            DestinationCipher destinationCipher,
            NotificationRetryPolicy retryPolicy,
            NotificationPreferenceStore preferences,
            Clock clock,
            NotificationWorkerProperties properties) {
        this.store = store;
        this.emailProvider = emailProvider;
        this.smsProvider = smsProvider;
        this.destinationCipher = destinationCipher;
        this.retryPolicy = retryPolicy;
        this.preferences = preferences;
        this.clock = clock;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${edutrack.notifications.worker.poll-delay-ms:5000}")
    public void processOnce() {
        Instant now = clock.instant();
        List<DeliveryWorkItem> claimed = store.claimDue(properties.getBatchSize(), now,
                now.plusSeconds(properties.getLeaseSeconds()));
        for (DeliveryWorkItem item : claimed) {
            deliver(item);
        }
    }

    private void deliver(DeliveryWorkItem item) {
        if (NotificationCategory.OPTIONAL.name().equals(item.category())
                && !preferences.isEnabled(item.schoolId(),
                NotificationRecipientType.valueOf(item.recipientType()), item.recipientId(), item.channel())) {
            store.markSkipped(item.messageId(), item.attempts(), "PREFERENCE_DISABLED", clock.instant());
            return;
        }
        boolean enabled = item.channel() == NotificationChannel.EMAIL
                ? emailProvider.isEnabled() : smsProvider.isEnabled();
        if (!enabled) {
            store.markSkipped(item.messageId(), item.attempts(), "PROVIDER_DISABLED", clock.instant());
            return;
        }

        final String destination;
        try {
            destination = destinationCipher.decrypt(item.destinationEncrypted());
        } catch (IllegalStateException | IllegalArgumentException exception) {
            store.markFailed(item.messageId(), item.attempts(),
                    NotificationMessageStatus.FAILED, "DESTINATION_UNAVAILABLE");
            log.warn("Notification delivery failed to decrypt destination; messageId={}", item.messageId());
            return;
        }

        store.markSending(item.messageId(), item.attempts());
        try {
            ProviderReceipt receipt;
            if (item.channel() == NotificationChannel.EMAIL) {
                receipt = emailProvider.send(new EmailProvider.EmailDelivery(
                        destination, item.subject(), item.bodyRendered(), item.html(), item.idempotencyKey()));
            } else {
                receipt = smsProvider.send(new SmsProvider.SmsDelivery(
                        destination, item.bodyRendered(), item.idempotencyKey()));
            }
            store.markSent(item.messageId(), item.attempts(),
                    receipt == null ? null : receipt.providerMessageId(), clock.instant());
        } catch (DeliveryFailureException exception) {
            handleFailure(item, exception);
        }
    }

    private void handleFailure(DeliveryWorkItem item, DeliveryFailureException failure) {
        String code = safeErrorCode(failure.getErrorCode());
        if (failure.isOutcomeUnknown()) {
            store.markFailed(item.messageId(), item.attempts(), NotificationMessageStatus.DEAD, code);
            log.warn("Notification provider outcome is unknown; messageId={}, channel={}",
                    item.messageId(), item.channel());
            return;
        }
        if (!failure.isRetryable()) {
            store.markFailed(item.messageId(), item.attempts(), NotificationMessageStatus.FAILED, code);
            log.info("Notification delivery permanently failed; messageId={}, channel={}",
                    item.messageId(), item.channel());
            return;
        }
        if (item.attempts() >= properties.getMaximumAttempts()) {
            store.markFailed(item.messageId(), item.attempts(), NotificationMessageStatus.DEAD, code);
            log.warn("Notification delivery exhausted retries; messageId={}, channel={}",
                    item.messageId(), item.channel());
            return;
        }
        Instant now = clock.instant();
        Instant nextAttemptAt = retryPolicy.nextAttemptAt(item.attempts(), now);
        if (Duration.between(now, nextAttemptAt).isNegative()) {
            throw new IllegalStateException("Notification retry policy returned a past instant");
        }
        store.scheduleRetry(item.messageId(), item.attempts(), nextAttemptAt, code);
    }

    private String safeErrorCode(String value) {
        if (value == null || !value.matches("[A-Z0-9_]{1,64}")) {
            return "DELIVERY_FAILURE";
        }
        return value;
    }
}
