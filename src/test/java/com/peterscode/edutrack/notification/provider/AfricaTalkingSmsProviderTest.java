package com.peterscode.edutrack.notification.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AfricaTalkingSmsProviderTest {

    @Test
    void sendsSandboxFormAndReturnsProviderMessageId() throws Exception {
        String response = """
                {"SMSMessageData":{"Recipients":[{"statusCode":101,"number":"+254712345678",
                "status":"Success","messageId":"AT-test-id"}]}}
                """;
        withServer((server, requestCount) -> {
            server.createContext("/version1/messaging", exchange -> {
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                assertThat(exchange.getRequestHeaders().getFirst("apiKey")).isEqualTo("test-key");
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                assertThat(body).contains("username=sandbox", "to=%2B254712345678", "message=hello",
                        "from=EduTrack", "bulkSMSMode=1", "enqueue=1");
                respond(exchange, 200, response);
            });
        }, provider -> assertThat(provider.send(delivery()).providerMessageId()).isEqualTo("AT-test-id"));
    }

    @Test
    void retriesTransientRateLimitResponseOnTheNextWorkerAttempt() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        withServer((server, ignored) -> server.createContext("/version1/messaging", exchange -> {
            if (attempts.incrementAndGet() == 1) {
                respond(exchange, 429, "{}");
            } else {
                respond(exchange, 200, """
                        {"SMSMessageData":{"Recipients":[{"statusCode":101,"status":"Success",
                        "messageId":"AT-retried-id"}]}}
                        """);
            }
        }), provider -> {
            assertThatThrownBy(() -> provider.send(delivery()))
                    .isInstanceOf(DeliveryFailureException.class)
                    .satisfies(error -> {
                        DeliveryFailureException failure = (DeliveryFailureException) error;
                        assertThat(failure.isRetryable()).isTrue();
                        assertThat(failure.isOutcomeUnknown()).isFalse();
                    });
            assertThat(provider.send(delivery()).providerMessageId()).isEqualTo("AT-retried-id");
            assertThat(attempts).hasValue(2);
        });
    }

    @Test
    void rejectsPermanentClientErrorsWithoutRetry() throws Exception {
        withServer((server, ignored) -> server.createContext("/version1/messaging",
                exchange -> respond(exchange, 400, "{}")), provider ->
                assertThatThrownBy(() -> provider.send(delivery()))
                        .isInstanceOf(DeliveryFailureException.class)
                        .satisfies(error -> {
                            DeliveryFailureException failure = (DeliveryFailureException) error;
                            assertThat(failure.isRetryable()).isFalse();
                            assertThat(failure.isOutcomeUnknown()).isFalse();
                        }));
    }

    @Test
    void marksTimeoutAsUnknownOutcomeToAvoidDuplicateSms() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/version1/messaging", exchange -> {
            try {
                Thread.sleep(400);
                respond(exchange, 200, """
                        {"SMSMessageData":{"Recipients":[{"statusCode":101,"status":"Success",
                        "messageId":"too-late"}]}}
                        """);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            server.start();
            java.net.URI base = java.net.URI.create(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/version1/");
            AfricaTalkingSmsProperties properties = properties(base, 100);
            AfricaTalkingSmsProvider provider = new AfricaTalkingSmsProvider(properties, new ObjectMapper());
            assertThatThrownBy(() -> provider.send(delivery()))
                    .isInstanceOf(DeliveryFailureException.class)
                    .satisfies(error -> {
                        DeliveryFailureException failure = (DeliveryFailureException) error;
                        assertThat(failure.isRetryable()).isFalse();
                        assertThat(failure.isOutcomeUnknown()).isTrue();
                    });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void treatsMalformedProviderResponseAsUnknownOutcome() throws Exception {
        withServer((server, ignored) -> server.createContext("/version1/messaging",
                exchange -> respond(exchange, 200, "not-json")), provider ->
                assertThatThrownBy(() -> provider.send(delivery()))
                        .isInstanceOf(DeliveryFailureException.class)
                        .satisfies(error -> assertThat(((DeliveryFailureException) error).isOutcomeUnknown())
                                .isTrue()));
    }

    private void withServer(ServerConfigurer configure, ProviderAction action) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            configure.configure(server, new AtomicInteger());
            server.start();
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/version1/";
            action.run(new AfricaTalkingSmsProvider(properties(java.net.URI.create(base), 2_000),
                    new ObjectMapper()));
        } finally {
            server.stop(0);
        }
    }

    private AfricaTalkingSmsProperties properties(java.net.URI baseUrl, int timeoutMillis) {
        AfricaTalkingSmsProperties properties = new AfricaTalkingSmsProperties();
        properties.setUsername("sandbox");
        properties.setApiKey("test-key");
        properties.setSenderId("EduTrack");
        properties.setBaseUrl(baseUrl.toString());
        properties.setConnectTimeoutMillis(1_000);
        properties.setRequestTimeoutMillis(timeoutMillis);
        properties.setMaximumMessageCharacters(1_600);
        return properties;
    }

    private SmsProvider.SmsDelivery delivery() {
        return new SmsProvider.SmsDelivery("+254712345678", "hello", "request:message");
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    @FunctionalInterface
    private interface ServerConfigurer {
        void configure(HttpServer server, AtomicInteger requests);
    }

    @FunctionalInterface
    private interface ProviderAction {
        void run(AfricaTalkingSmsProvider provider) throws Exception;
    }
}
