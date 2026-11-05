package com.peterscode.edutrack.notification.provider;

public interface SmsProvider {

    boolean isEnabled();

    ProviderReceipt send(SmsDelivery delivery) throws DeliveryFailureException;

    record SmsDelivery(String destination, String body, String idempotencyKey) {
    }
}
