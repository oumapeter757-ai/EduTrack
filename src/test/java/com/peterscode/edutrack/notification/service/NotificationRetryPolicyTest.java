package com.peterscode.edutrack.notification.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRetryPolicyTest {

    @Test
    void retryDelayUsesExponentialBackoffAndConfiguredMaximum() {
        NotificationWorkerProperties properties = new NotificationWorkerProperties();
        properties.setRetryBaseSeconds(10);
        properties.setRetryMaximumSeconds(25);
        NotificationRetryPolicy policy = new NotificationRetryPolicy(properties);
        Instant now = Instant.parse("2026-11-04T15:00:00Z");

        Duration first = Duration.between(now, policy.nextAttemptAt(1, now));
        Duration second = Duration.between(now, policy.nextAttemptAt(2, now));
        Duration capped = Duration.between(now, policy.nextAttemptAt(8, now));

        assertThat(first.getSeconds()).isBetween(8L, 12L);
        assertThat(second.getSeconds()).isBetween(16L, 24L);
        assertThat(capped.getSeconds()).isBetween(20L, 25L);
    }
}
