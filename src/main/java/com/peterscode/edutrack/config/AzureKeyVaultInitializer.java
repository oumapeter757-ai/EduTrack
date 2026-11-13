package com.peterscode.edutrack.config;

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

public class AzureKeyVaultInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Map<String, String> REQUIRED_SECRETS = Map.of(
            "DB_PASSWORD", "db-password",
            "JWT_SECRET_KEY", "jwt-private-key",
            "EDUTRACK_FEES_ENCRYPTION_KEY", "fees-encryption-key",
            "REDIS_PASSWORD", "redis-password");

    private static final Map<String, String> OPTIONAL_SECRETS = Map.of(
            "SPRING_MAIL_PASSWORD", "mail-password",
            "EDUTRACK_NOTIFICATION_SMS_API_KEY", "sms-api-key",
            "EDUTRACK_NOTIFICATION_DESTINATION_ENCRYPTION_KEY", "notification-destination-encryption-key",
            "EDUTRACK_NOTIFICATION_OPT_OUT_SIGNING_KEY", "notification-opt-out-signing-key",
            "MPESA_CONSUMER_KEY", "mpesa-consumer-key",
            "MPESA_CONSUMER_SECRET", "mpesa-consumer-secret",
            "MPESA_PASSKEY", "mpesa-passkey",
            "MPESA_PAYMENT_WEBHOOK_TOKEN", "mpesa-payment-webhook-token");

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        String vaultUri = context.getEnvironment().getProperty("EDUTRACK_KEY_VAULT_URI");
        if (vaultUri == null || vaultUri.isBlank()) {
            return;
        }

        SecretClient secretClient = new SecretClientBuilder()
                .vaultUrl(vaultUri)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
        Map<String, Object> properties = new LinkedHashMap<>();
        REQUIRED_SECRETS.forEach((propertyName, secretName) ->
                properties.put(propertyName, getRequiredSecret(secretClient, secretName)));
        OPTIONAL_SECRETS.forEach((propertyName, secretName) ->
                getOptionalSecret(secretClient, secretName)
                        .ifPresent(value -> properties.put(propertyName, value)));
        context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("azureKeyVault", properties));
    }

    private String getRequiredSecret(SecretClient secretClient, String secretName) {
        try {
            return secretClient.getSecret(secretName).getValue();
        } catch (ResourceNotFoundException exception) {
            throw new IllegalStateException("Required Key Vault secret is missing: " + secretName, exception);
        }
    }

    private java.util.Optional<String> getOptionalSecret(SecretClient secretClient, String secretName) {
        try {
            KeyVaultSecret secret = secretClient.getSecret(secretName);
            return java.util.Optional.ofNullable(secret.getValue());
        } catch (ResourceNotFoundException exception) {
            return java.util.Optional.empty();
        }
    }
}
