package com.peterscode.edutrack.notification.provider;

public class DisabledEmailProvider implements EmailProvider {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public ProviderReceipt send(EmailDelivery delivery) {
        throw new IllegalStateException("Email provider is disabled");
    }
}
