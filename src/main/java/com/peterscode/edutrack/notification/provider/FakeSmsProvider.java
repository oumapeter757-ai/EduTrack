package com.peterscode.edutrack.notification.provider;

import java.util.UUID;

public class FakeSmsProvider implements SmsProvider {

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public ProviderReceipt send(SmsDelivery delivery) {
        return new ProviderReceipt("fake-sms-" + UUID.randomUUID());
    }
}
