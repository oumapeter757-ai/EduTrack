package com.peterscode.edutrack.notification.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peterscode.edutrack.shared.PhoneNumberNormalizer;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class AfricaTalkingSmsProvider implements SmsProvider {

    private final String username;
    private final String apiKey;
    private final String senderId;
    private final URI endpoint;
    private final int requestTimeoutMillis;
    private final int maximumMessageCharacters;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AfricaTalkingSmsProvider(AfricaTalkingSmsProperties properties, ObjectMapper objectMapper) {
        this.username = properties.getUsername().trim();
        this.apiKey = properties.getApiKey();
        this.senderId = properties.getSenderId().trim();
        this.endpoint = messagingEndpoint(URI.create(properties.getBaseUrl()));
        this.requestTimeoutMillis = properties.getRequestTimeoutMillis();
        this.maximumMessageCharacters = properties.getMaximumMessageCharacters();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public ProviderReceipt send(SmsDelivery delivery) throws DeliveryFailureException {
        if (delivery == null || delivery.destination() == null || delivery.body() == null
                || delivery.body().isBlank() || delivery.body().length() > maximumMessageCharacters) {
            throw new DeliveryFailureException("INVALID_SMS", false, false);
        }
        String destination;
        try {
            destination = PhoneNumberNormalizer.normalize(delivery.destination());
        } catch (IllegalArgumentException ex) {
            throw new DeliveryFailureException("INVALID_DESTINATION", false, false);
        }

        StringBuilder form = new StringBuilder()
                .append(field("username", username))
                .append('&').append(field("to", destination))
                .append('&').append(field("message", delivery.body()))
                .append("&bulkSMSMode=1&enqueue=1");
        if (!senderId.isBlank()) {
            form.append('&').append(field("from", senderId));
        }
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofMillis(requestTimeoutMillis))
                .header("apiKey", apiKey)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new DeliveryFailureException("PROVIDER_TIMEOUT", false, true);
        } catch (ConnectException ex) {
            throw new DeliveryFailureException("PROVIDER_UNAVAILABLE", true, false);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DeliveryFailureException("PROVIDER_INTERRUPTED", false, true);
        } catch (IOException ex) {
            throw new DeliveryFailureException("PROVIDER_IO_FAILURE", false, true);
        }

        if (response.statusCode() == 429) {
            throw new DeliveryFailureException("PROVIDER_RATE_LIMITED", true, false);
        }
        if (response.statusCode() >= 500) {
            throw new DeliveryFailureException("PROVIDER_SERVER_ERROR", false, true);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new DeliveryFailureException("PROVIDER_REJECTED", false, false);
        }
        try {
            JsonNode recipients = objectMapper.readTree(response.body())
                    .path("SMSMessageData").path("Recipients");
            if (!recipients.isArray() || recipients.size() != 1) {
                throw new DeliveryFailureException("PROVIDER_RESPONSE_INVALID", false, true);
            }
            JsonNode recipient = recipients.get(0);
            String messageId = recipient.path("messageId").asText("");
            JsonNode statusCode = recipient.get("statusCode");
            if (statusCode == null || !statusCode.canConvertToInt()) {
                throw new DeliveryFailureException("PROVIDER_RESPONSE_INVALID", false, true);
            }
            if (statusCode.intValue() != 101) {
                throw new DeliveryFailureException("PROVIDER_REJECTED", false, false);
            }
            if (messageId.isBlank() || messageId.length() > 190) {
                throw new DeliveryFailureException("PROVIDER_RESPONSE_INVALID", false, true);
            }
            return new ProviderReceipt(messageId);
        } catch (DeliveryFailureException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DeliveryFailureException("PROVIDER_RESPONSE_INVALID", false, true);
        }
    }

    private URI messagingEndpoint(URI baseUrl) {
        if (baseUrl == null || !baseUrl.isAbsolute()
                || (!"https".equalsIgnoreCase(baseUrl.getScheme())
                    && !("http".equalsIgnoreCase(baseUrl.getScheme())
                         && ("localhost".equalsIgnoreCase(baseUrl.getHost())
                             || "127.0.0.1".equals(baseUrl.getHost()))))) {
            throw new IllegalStateException("Africa's Talking SMS base URL must use HTTPS");
        }
        String value = baseUrl.toString();
        return URI.create((value.endsWith("/") ? value : value + "/") + "messaging");
    }

    private String field(String key, String value) {
        return encode(key) + "=" + encode(value);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
