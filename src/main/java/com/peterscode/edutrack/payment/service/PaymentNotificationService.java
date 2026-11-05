package com.peterscode.edutrack.payment.service;

import com.peterscode.edutrack.notification.entity.NotificationCategory;
import com.peterscode.edutrack.notification.entity.NotificationRecipientType;
import com.peterscode.edutrack.notification.service.NotificationMessageComposer;
import com.peterscode.edutrack.notification.service.NotificationOutboxService;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.EnqueueMessage;
import com.peterscode.edutrack.payment.entity.PaymentInvoice;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class PaymentNotificationService {

    private final UserRepository userRepository;
    private final NotificationMessageComposer composer;
    private final NotificationOutboxService outbox;

    public PaymentNotificationService(UserRepository userRepository,
                                     NotificationMessageComposer composer,
                                     NotificationOutboxService outbox) {
        this.userRepository = userRepository;
        this.composer = composer;
        this.outbox = outbox;
    }

    @Transactional
    public void sendPaymentConfirmationEmails(PaymentInvoice invoice) {
        if (invoice == null || invoice.getSchool() == null || invoice.getId() == null) {
            throw new IllegalArgumentException("Confirmed payment details are required");
        }
        School school = invoice.getSchool();
        String key = "payment-confirmation:" + invoice.getId();
        String payload = "{\"invoiceId\":" + invoice.getId() + "}";

        List<NotificationMessageComposer.Recipient> schoolRecipients = new ArrayList<>();
        if (school.getEmail() != null && !school.getEmail().isBlank()) {
            schoolRecipients.add(new NotificationMessageComposer.Recipient(
                    NotificationRecipientType.SCHOOL_ADMIN, school.getId(), school.getEmail(), null));
        }
        enqueue(school.getId(), "PAYMENT_CONFIRMED", key, payload, schoolRecipients,
                "Subscription payment confirmed",
                "Your EduTrack subscription payment has been confirmed. Invoice "
                        + invoice.getInvoiceNumber() + " for " + invoice.getCurrency() + " "
                        + invoice.getAmount() + " is now active.");

        List<NotificationMessageComposer.Recipient> auditRecipients = userRepository.findByRole(Role.SUPER_ADMIN)
                .stream()
                .filter(user -> user.isActive() && user.getEmail() != null && !user.getEmail().isBlank())
                .map(user -> new NotificationMessageComposer.Recipient(
                        NotificationRecipientType.SUPER_ADMIN, user.getId(), user.getEmail(), null))
                .toList();
        enqueue(school.getId(), "PAYMENT_CONFIRMED", key, payload, auditRecipients,
                "[Audit] Subscription payment confirmed",
                "A subscription payment was confirmed for school " + school.getName()
                        + ". Invoice " + invoice.getInvoiceNumber() + ", amount "
                        + invoice.getCurrency() + " " + invoice.getAmount() + ".");
    }

    @Transactional
    public void sendPaymentReceipt(PaymentInvoice invoice) {
        if (invoice == null || invoice.getSchool() == null || invoice.getId() == null) {
            throw new IllegalArgumentException("Confirmed payment details are required");
        }
        School school = invoice.getSchool();
        if (school.getEmail() == null || school.getEmail().isBlank()) {
            return;
        }
        List<NotificationMessageComposer.Recipient> recipients = List.of(
                new NotificationMessageComposer.Recipient(NotificationRecipientType.SCHOOL_ADMIN,
                        school.getId(), school.getEmail(), null));
        enqueue(school.getId(), "PAYMENT_RECEIPT", "payment-receipt:" + invoice.getId(),
                "{\"invoiceId\":" + invoice.getId() + "}", recipients,
                "Subscription payment receipt",
                "Your EduTrack subscription payment has been received. Invoice "
                        + invoice.getInvoiceNumber() + " for " + invoice.getCurrency() + " "
                        + invoice.getAmount() + ".");
    }

    private void enqueue(long schoolId, String eventType, String eventKey, String payload,
                         List<NotificationMessageComposer.Recipient> recipients,
                         String subject, String body) {
        List<EnqueueMessage> messages = composer.compose(schoolId, recipients, subject, body,
                NotificationCategory.ESSENTIAL);
        if (!messages.isEmpty()) {
            outbox.enqueue(schoolId, eventType, eventKey, payload, messages);
        }
    }
}
