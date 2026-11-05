package com.peterscode.edutrack.notification.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NotificationProviderConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NotificationProviderConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void providersAreDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(EmailProvider.class);
            assertThat(context).hasSingleBean(SmsProvider.class);
            assertThat(context.getBean(EmailProvider.class).isEnabled()).isFalse();
            assertThat(context.getBean(SmsProvider.class).isEnabled()).isFalse();
        });
    }

    @Test
    void sandboxProviderCanBeConfiguredForLocalMockServer() {
        contextRunner
                .withPropertyValues(
                        "edutrack.notifications.email.enabled=true",
                        "edutrack.notifications.email.provider=fake",
                        "edutrack.notifications.destination-encryption-key=AAAAAAAAAAAAAAAAAAAAAA==",
                        "edutrack.notifications.sms.enabled=true",
                        "edutrack.notifications.sms.mode=SANDBOX",
                        "edutrack.notifications.sms.username=sandbox",
                        "edutrack.notifications.sms.api-key=test-api-key",
                        "edutrack.notifications.sms.base-url=http://localhost:8765/version1/")
                .run(context -> {
                    assertThat(context).hasSingleBean(FakeEmailProvider.class);
                    assertThat(context).hasSingleBean(AfricaTalkingSmsProvider.class);
                    assertThat(context.getBean(EmailProvider.class).isEnabled()).isTrue();
                    assertThat(context.getBean(SmsProvider.class).isEnabled()).isTrue();
                });
    }

    @Test
    void liveModeRefusesToStartWithoutAnApiKey() {
        contextRunner
                .withPropertyValues(
                        "edutrack.notifications.sms.enabled=true",
                        "edutrack.notifications.sms.mode=LIVE",
                        "edutrack.notifications.sms.username=edutrack")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("Africa's Talking SMS API key is required");
                });
    }

    @Test
    void smtpStartupFailsClearlyWhenCredentialsAreMissing() {
        contextRunner
                .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .withPropertyValues(
                        "edutrack.notifications.email.enabled=true",
                        "edutrack.notifications.email.provider=smtp",
                        "edutrack.notifications.email.from=notifications@example.test",
                        "edutrack.notifications.destination-encryption-key=AAAAAAAAAAAAAAAAAAAAAA==")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("SMTP provider is enabled but credentials are missing");
                });
    }
}
