package com.peterscode.edutrack.notification.provider;

import java.util.UUID;

public class FakeEmailProvider implements EmailProvider {

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public ProviderReceipt send(EmailDelivery delivery) {
        return new ProviderReceipt("fake-email-" + UUID.randomUUID());
    }
}
