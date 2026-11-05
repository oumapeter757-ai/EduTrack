package com.peterscode.edutrack.notification.provider;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "edutrack.notifications.sms")
public class AfricaTalkingSmsProperties {

    private boolean enabled;
    @NotNull
    private SmsProviderMode mode = SmsProviderMode.DISABLED;
    private String username = "";
    private String apiKey = "";
    private String senderId = "";
    private String baseUrl = "";
    @Min(100)
    @Max(60_000)
    private int connectTimeoutMillis = 3_000;
    @Min(100)
    @Max(120_000)
    private int requestTimeoutMillis = 10_000;
    @Min(1)
    @Max(1_600)
    private int maximumMessageCharacters = 1_600;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public SmsProviderMode getMode() {
        return mode;
    }

    public void setMode(SmsProviderMode mode) {
        this.mode = mode;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username == null ? "" : username;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId == null ? "" : senderId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getRequestTimeoutMillis() {
        return requestTimeoutMillis;
    }

    public void setRequestTimeoutMillis(int requestTimeoutMillis) {
        this.requestTimeoutMillis = requestTimeoutMillis;
    }

    public int getMaximumMessageCharacters() {
        return maximumMessageCharacters;
    }

    public void setMaximumMessageCharacters(int maximumMessageCharacters) {
        this.maximumMessageCharacters = maximumMessageCharacters;
    }
}
