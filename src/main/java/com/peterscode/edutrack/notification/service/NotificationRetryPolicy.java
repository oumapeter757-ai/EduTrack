package com.peterscode.edutrack.notification.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class NotificationRetryPolicy {

    private final NotificationWorkerProperties properties;

    public NotificationRetryPolicy(NotificationWorkerProperties properties) {
        this.properties = properties;
    }

    public Instant nextAttemptAt(int attempt, Instant now) {
        if (attempt < 1) {
            throw new IllegalArgumentException("Attempt must be positive");
        }
        long base = properties.getRetryBaseSeconds();
        long maximum = properties.getRetryMaximumSeconds();
        int exponent = Math.min(attempt - 1, 30);
        long delay = Math.min(maximum, base * (1L << exponent));
        long jittered = Math.max(1L, Math.round(delay * ThreadLocalRandom.current().nextDouble(0.8, 1.2)));
        return now.plusSeconds(Math.min(maximum, jittered));
    }
}
