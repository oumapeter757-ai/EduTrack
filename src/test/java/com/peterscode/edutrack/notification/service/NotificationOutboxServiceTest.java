package com.peterscode.edutrack.notification.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.EnqueueMessage;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.NotificationChannel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class NotificationOutboxServiceTest {

    private final NotificationOutboxStore store = mock(NotificationOutboxStore.class);
    private final NotificationOutboxService service =
            new NotificationOutboxService(store, JsonMapper.builder().build());

    @Test
    void enqueuesMinimalNonSensitivePayloadWithStableEventKey() {
        EnqueueMessage message = message();
        when(store.enqueue(any())).thenReturn(41L);

        long requestId = service.enqueue(7L, "STUDENT_ABSENT", "absence:23:2026-11-04",
                "{\"studentId\":23,\"studentFirstName\":\"Amina\"}", List.of(message));

        assertThat(requestId).isEqualTo(41L);
        verify(store).enqueue(argThat(command ->
                command.schoolId().equals(7L)
                        && command.eventKey().equals("absence:23:2026-11-04")
                        && command.messages().size() == 1));
    }

    @Test
    void rejectsContactAndSensitiveFieldsInOutboxPayload() {
        assertThatThrownBy(() -> service.enqueue(7L, "STUDENT_ABSENT", "absence:23:date",
                "{\"studentId\":23,\"phone\":\"sensitive\"}", List.of(message())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notification payload contains a prohibited field");

        verifyNoInteractions(store);
    }

    @Test
    void rejectsEmailHeaderInjectionBeforePersistence() {
        EnqueueMessage message = new EnqueueMessage("PARENT", 12L, NotificationChannel.EMAIL,
                "m***@example.test", "encrypted", "EN", "Receipt\r\nBcc: other@example.test",
                "Receipt", false, "ESSENTIAL", 0, null, null);

        assertThatThrownBy(() -> service.enqueue(7L, "PAYMENT_RECEIVED", "receipt:123",
                "{\"paymentId\":123}", List.of(message)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notification message is invalid");

        verifyNoInteractions(store);
    }

    private EnqueueMessage message() {
        return new EnqueueMessage("PARENT", 12L, NotificationChannel.SMS,
                "+2547***123", "encrypted", "EN", null,
                "Amina was marked absent today.", false, "ESSENTIAL", 1, null, null);
    }
}
