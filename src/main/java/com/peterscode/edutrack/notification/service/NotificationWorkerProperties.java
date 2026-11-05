package com.peterscode.edutrack.notification.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "edutrack.notifications.worker")
public class NotificationWorkerProperties {

    @Min(1)
    @Max(500)
    private int batchSize = 50;

    @Min(10)
    @Max(3600)
    private int leaseSeconds = 120;

    @Min(1)
    @Max(20)
    private int maximumAttempts = 5;

    @Min(1)
    @Max(3600)
    private int retryBaseSeconds = 30;

    @Min(1)
    @Max(86_400)
    private int retryMaximumSeconds = 3600;

    @Min(100)
    @Max(60_000)
    private int pollDelayMillis = 5000;

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getLeaseSeconds() {
        return leaseSeconds;
    }

    public void setLeaseSeconds(int leaseSeconds) {
        this.leaseSeconds = leaseSeconds;
    }

    public int getMaximumAttempts() {
        return maximumAttempts;
    }

    public void setMaximumAttempts(int maximumAttempts) {
        this.maximumAttempts = maximumAttempts;
    }

    public int getRetryBaseSeconds() {
        return retryBaseSeconds;
    }

    public void setRetryBaseSeconds(int retryBaseSeconds) {
        this.retryBaseSeconds = retryBaseSeconds;
    }

    public int getRetryMaximumSeconds() {
        return retryMaximumSeconds;
    }

    public void setRetryMaximumSeconds(int retryMaximumSeconds) {
        this.retryMaximumSeconds = retryMaximumSeconds;
    }

    public int getPollDelayMillis() {
        return pollDelayMillis;
    }

    public void setPollDelayMillis(int pollDelayMillis) {
        this.pollDelayMillis = pollDelayMillis;
    }

    @AssertTrue(message = "Notification retry maximum must be at least the retry base")
    public boolean isRetryRangeValid() {
        return retryMaximumSeconds >= retryBaseSeconds;
    }
}
