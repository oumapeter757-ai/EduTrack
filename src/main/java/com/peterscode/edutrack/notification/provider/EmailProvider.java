package com.peterscode.edutrack.notification.provider;

public interface EmailProvider {

    boolean isEnabled();

    ProviderReceipt send(EmailDelivery delivery) throws DeliveryFailureException;

    record EmailDelivery(
            String destination,
            String subject,
            String body,
            boolean html,
            String idempotencyKey) {
    }
}
