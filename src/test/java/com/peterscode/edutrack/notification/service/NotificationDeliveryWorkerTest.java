package com.peterscode.edutrack.notification.service;

import com.peterscode.edutrack.notification.provider.DeliveryFailureException;
import com.peterscode.edutrack.notification.provider.EmailProvider;
import com.peterscode.edutrack.notification.provider.ProviderReceipt;
import com.peterscode.edutrack.notification.provider.SmsProvider;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.DeliveryWorkItem;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.NotificationChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationDeliveryWorkerTest {

    private final NotificationOutboxStore store = mock(NotificationOutboxStore.class);
    private final EmailProvider emailProvider = mock(EmailProvider.class);
    private final SmsProvider smsProvider = mock(SmsProvider.class);
    private final DestinationCipher cipher = mock(DestinationCipher.class);
    private final NotificationPreferenceStore preferences = mock(NotificationPreferenceStore.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-04T15:00:00Z"), ZoneOffset.UTC);
    private NotificationDeliveryWorker worker;

    @BeforeEach
    void setUp() {
        NotificationWorkerProperties properties = new NotificationWorkerProperties();
        properties.setBatchSize(10);
        properties.setLeaseSeconds(60);
        properties.setMaximumAttempts(5);
        properties.setRetryBaseSeconds(30);
        properties.setRetryMaximumSeconds(600);
        worker = new NotificationDeliveryWorker(store, emailProvider, smsProvider, cipher,
                new NotificationRetryPolicy(properties), preferences, clock, properties);
    }

    @Test
    void disabledProviderMarksMessageSkippedWithoutDecryptingDestination() throws Exception {
        when(store.claimDue(anyInt(), any(), any())).thenReturn(List.of(item(NotificationChannel.EMAIL, 1, 1)));
        when(emailProvider.isEnabled()).thenReturn(false);

        worker.processOnce();

        verify(store).markSkipped(eq(1L), eq(1), eq("PROVIDER_DISABLED"), any());
        verifyNoInteractions(cipher);
        verify(emailProvider, never()).send(any());
    }

    @Test
    void queuedOptionalNotificationIsSkippedAfterRecipientOptsOut() throws Exception {
        when(store.claimDue(anyInt(), any(), any())).thenReturn(List.of(item(
                NotificationChannel.EMAIL, 5, 1, "OPTIONAL")));
        when(preferences.isEnabled(7L, com.peterscode.edutrack.notification.entity.NotificationRecipientType.PARENT,
                1L, NotificationChannel.EMAIL)).thenReturn(false);

        worker.processOnce();

        verify(store).markSkipped(5L, 1, "PREFERENCE_DISABLED", clock.instant());
        verifyNoInteractions(cipher);
        verify(emailProvider, never()).send(any());
    }

    @Test
    void successfulDeliveryUsesStableIdempotencyKeyAndPersistsOnlyProviderReceipt() throws Exception {
        when(store.claimDue(anyInt(), any(), any())).thenReturn(List.of(item(NotificationChannel.EMAIL, 3, 1)));
        when(emailProvider.isEnabled()).thenReturn(true);
        when(cipher.decrypt("encrypted")).thenReturn("masked-test-destination");
        when(emailProvider.send(any())).thenReturn(new ProviderReceipt("provider-reference"));

        worker.processOnce();

        verify(store).markSending(3L, 1);
        verify(emailProvider).send(argThat(delivery -> delivery.idempotencyKey().equals("event-key")));
        verify(store).markSent(3L, 1, "provider-reference", clock.instant());
    }

    @Test
    void knownTransientProviderFailureSchedulesRetry() throws Exception {
        when(store.claimDue(anyInt(), any(), any())).thenReturn(List.of(item(NotificationChannel.EMAIL, 1, 1)));
        when(emailProvider.isEnabled()).thenReturn(true);
        when(cipher.decrypt("encrypted")).thenReturn("masked-test-destination");
        when(emailProvider.send(any())).thenThrow(new DeliveryFailureException("TEMPORARY_FAILURE", true, false));

        worker.processOnce();

        verify(store).scheduleRetry(eq(1L), eq(1), any(), eq("TEMPORARY_FAILURE"));
        verify(store, never()).markFailed(anyLong(), anyInt(), any(), any());
    }

    @Test
    void unknownSmsOutcomeIsNotRetriedToAvoidDuplicateMessages() throws Exception {
        when(store.claimDue(anyInt(), any(), any())).thenReturn(List.of(item(NotificationChannel.SMS, 2, 1)));
        when(smsProvider.isEnabled()).thenReturn(true);
        when(cipher.decrypt("encrypted")).thenReturn("+254700000000");
        when(smsProvider.send(any())).thenThrow(new DeliveryFailureException("OUTCOME_UNKNOWN", true, true));

        worker.processOnce();

        verify(store).markFailed(2L, 1, NotificationMessageStatus.DEAD, "OUTCOME_UNKNOWN");
        verify(store, never()).scheduleRetry(anyLong(), anyInt(), any(), anyString());
    }

    @Test
    void exhaustedTransientFailureMovesMessageToDeadList() throws Exception {
        when(store.claimDue(anyInt(), any(), any())).thenReturn(List.of(item(NotificationChannel.EMAIL, 4, 5)));
        when(emailProvider.isEnabled()).thenReturn(true);
        when(cipher.decrypt("encrypted")).thenReturn("masked-test-destination");
        when(emailProvider.send(any())).thenThrow(new DeliveryFailureException("TEMPORARY_FAILURE", true, false));

        worker.processOnce();

        verify(store).markFailed(4L, 5, NotificationMessageStatus.DEAD, "TEMPORARY_FAILURE");
        verify(store, never()).scheduleRetry(anyLong(), anyInt(), any(), anyString());
    }

    private DeliveryWorkItem item(NotificationChannel channel, long id, int attempts) {
        return item(channel, id, attempts, "ESSENTIAL");
    }

    private DeliveryWorkItem item(NotificationChannel channel, long id, int attempts, String category) {
        return new DeliveryWorkItem(id, 7L, "PARENT", 1L, channel, category,
                "encrypted", "user@example.test",
                "Subject", "Hello", false, attempts, "event-key");
    }
}
