package com.peterscode.edutrack.notification.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
@EnableConfigurationProperties(AfricaTalkingSmsProperties.class)
public class NotificationProviderConfiguration {

    @Bean
    @ConditionalOnProperty(name = "edutrack.notifications.email.enabled", havingValue = "false", matchIfMissing = true)
    EmailProvider disabledEmailProvider() {
        return new DisabledEmailProvider();
    }

    @Bean
    @ConditionalOnProperty(name = "edutrack.notifications.email.enabled", havingValue = "true")
    @ConditionalOnProperty(name = "edutrack.notifications.email.provider", havingValue = "fake", matchIfMissing = true)
    EmailProvider fakeEmailProvider(
            @Value("${edutrack.notifications.destination-encryption-key:}") String encryptionKey) {
        requireEncryptionKey(encryptionKey);
        return new FakeEmailProvider();
    }

    @Bean
    @ConditionalOnProperty(name = "edutrack.notifications.email.enabled", havingValue = "true")
    @ConditionalOnProperty(name = "edutrack.notifications.email.provider", havingValue = "smtp")
    EmailProvider smtpEmailProvider(JavaMailSender sender,
                                    @Value("${edutrack.notifications.email.from:}") String fromAddress,
                                    @Value("${spring.mail.username:}") String username,
                                    @Value("${spring.mail.password:}") String password,
                                    @Value("${edutrack.notifications.destination-encryption-key:}") String encryptionKey) {
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("SMTP provider is enabled but credentials are missing");
        }
        if (fromAddress.isBlank()) {
            throw new IllegalStateException("SMTP provider is enabled but sender address is missing");
        }
        requireEncryptionKey(encryptionKey);
        return new SmtpEmailProvider(sender, fromAddress);
    }

    @Bean
    @ConditionalOnProperty(name = "edutrack.notifications.sms.enabled", havingValue = "false", matchIfMissing = true)
    SmsProvider disabledSmsProvider() {
        return new DisabledSmsProvider();
    }

    @Bean
    @ConditionalOnProperty(name = "edutrack.notifications.sms.enabled", havingValue = "true")
    SmsProvider configuredSmsProvider(AfricaTalkingSmsProperties properties, ObjectMapper objectMapper,
                                      @Value("${edutrack.notifications.destination-encryption-key:}")
                                      String encryptionKey) {
        if (properties.getMode() == SmsProviderMode.DISABLED) {
            throw new IllegalStateException("SMS is enabled but the provider mode is DISABLED");
        }
        if (properties.getMode() == SmsProviderMode.SANDBOX
                && properties.getUsername().isBlank()) {
            properties.setUsername("sandbox");
        }
        if (properties.getUsername().isBlank()) {
            throw new IllegalStateException("Africa's Talking SMS username is required");
        }
        if (properties.getApiKey().isBlank()) {
            throw new IllegalStateException("Africa's Talking SMS API key is required");
        }
        requireEncryptionKey(encryptionKey);
        if (properties.getBaseUrl().isBlank()) {
            properties.setBaseUrl(
                    properties.getMode() == SmsProviderMode.SANDBOX
                            ? "https://api.sandbox.africastalking.com/version1/"
                            : "https://api.africastalking.com/version1/");
        }
        return new AfricaTalkingSmsProvider(properties, objectMapper);
    }

    private void requireEncryptionKey(String encryptionKey) {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalStateException("Notification provider is enabled but destination encryption is not configured");
        }
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(encryptionKey);
            if (decoded.length != 16 && decoded.length != 24 && decoded.length != 32) {
                throw new IllegalStateException("Notification destination encryption key must be a base64 AES key");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Notification destination encryption key must be a base64 AES key");
        }
    }
}
