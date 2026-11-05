package com.peterscode.edutrack.notification.provider;

public class DisabledSmsProvider implements SmsProvider {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public ProviderReceipt send(SmsDelivery delivery) {
        throw new IllegalStateException("SMS provider is disabled");
    }
}
