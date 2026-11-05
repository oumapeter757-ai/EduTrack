package com.peterscode.edutrack.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.EnqueueCommand;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.EnqueueMessage;
import com.peterscode.edutrack.notification.entity.NotificationCategory;
import com.peterscode.edutrack.notification.entity.NotificationRecipientType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class NotificationOutboxService {

    private static final int MAX_PAYLOAD_LENGTH = 65_536;
    private static final Set<String> FORBIDDEN_PAYLOAD_KEY_PARTS = Set.of(
            "email", "phone", "telephone", "destination", "recipient", "address",
            "body", "message", "password", "token", "medical", "score", "grade", "balance"
    );

    private final NotificationOutboxStore store;
    private final ObjectMapper objectMapper;

    public NotificationOutboxService(NotificationOutboxStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public long enqueue(Long schoolId, String eventType, String eventKey, String payloadJson,
                       List<EnqueueMessage> messages) {
        if (schoolId == null || schoolId < 1) {
            throw new IllegalArgumentException("A valid school id is required");
        }
        if (eventType == null || eventType.isBlank() || eventType.length() > 64
                || eventKey == null || eventKey.isBlank() || eventKey.length() > 190) {
            throw new IllegalArgumentException("Notification event identity is invalid");
        }
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("At least one notification message is required");
        }
        validatePayload(payloadJson);
        for (EnqueueMessage message : messages) {
            validateMessage(message);
        }
        return store.enqueue(new EnqueueCommand(schoolId, eventType, eventKey, payloadJson, List.copyOf(messages)));
    }

    private void validatePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.length() > MAX_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("Notification payload is missing or too large");
        }
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("Notification payload must be a JSON object");
            }
            rejectSensitiveKeys(root);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Notification payload must be valid JSON");
        }
    }

    private void rejectSensitiveKeys(JsonNode node) {
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next().toLowerCase(Locale.ROOT);
                if (FORBIDDEN_PAYLOAD_KEY_PARTS.stream().anyMatch(name::contains)) {
                    throw new IllegalArgumentException("Notification payload contains a prohibited field");
                }
            }
            node.elements().forEachRemaining(this::rejectSensitiveKeys);
        } else if (node.isArray()) {
            node.elements().forEachRemaining(this::rejectSensitiveKeys);
        }
    }

    private void validateMessage(EnqueueMessage message) {
        boolean invalidEmailSubject = message != null
                && message.channel() == NotificationOutboxStore.NotificationChannel.EMAIL
                && (message.subject() == null || message.subject().isBlank()
                    || message.subject().indexOf('\r') >= 0 || message.subject().indexOf('\n') >= 0);
        if (message == null || message.recipientType() == null || message.recipientId() == null
                || message.recipientId() < 1 || message.channel() == null
                || !isEnumValue(NotificationRecipientType.class, message.recipientType())
                || !isEnumValue(NotificationCategory.class, message.category())
                || message.destinationEncrypted() == null || message.destinationEncrypted().isBlank()
                || message.destinationEncrypted().length() > 2048
                || message.destinationMasked() == null || message.destinationMasked().length() > 254
                || message.bodyRendered() == null || message.bodyRendered().length() > 20_000
                || message.category() == null || message.language() == null
                || message.language().length() > 8
                || (message.segments() != null && message.segments() < 0)
                || message.smsQuotaLimit() < 0 || message.smsQuotaLimit() > 10_000_000
                || (message.channel() == NotificationOutboxStore.NotificationChannel.EMAIL
                    && message.smsQuotaLimit() != 0)
                || invalidEmailSubject
                || (message.subject() != null && message.subject().length() > 500)
                || (message.channel() == NotificationOutboxStore.NotificationChannel.SMS
                    && (message.subject() != null || message.html()
                        || message.bodyRendered().length() > 1600))) {
            throw new IllegalArgumentException("Notification message is invalid");
        }
    }

    private boolean isEnumValue(Class<? extends Enum<?>> type, String value) {
        if (value == null) {
            return false;
        }
        for (Enum<?> constant : type.getEnumConstants()) {
            if (constant.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
