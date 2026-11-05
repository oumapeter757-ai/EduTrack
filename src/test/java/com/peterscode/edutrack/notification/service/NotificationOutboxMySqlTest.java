package com.peterscode.edutrack.notification.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.EnqueueMessage;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.NotificationChannel;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class NotificationOutboxMySqlTest {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:9.7")
            .withDatabaseName("notification_test")
            .withUsername("test")
            .withPassword("test")
            .withTmpFs(Map.of("/var/lib/mysql", "rw"))
            .withConfigurationOverride("mysql")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withReuse(false);

    private static JdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static NotificationOutboxService outbox;
    private static JdbcNotificationOutboxStore store;

    @BeforeAll
    static void setUpDatabase() {
        DataSource dataSource = new DriverManagerDataSource(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE schools (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY
                ) ENGINE=InnoDB
                """);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("51")
                .target("54")
                .load()
                .migrate();
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        store = new JdbcNotificationOutboxStore(jdbc, java.time.Clock.systemUTC(),
                new NotificationSmsQuotaProperties());
        outbox = new NotificationOutboxService(store, JsonMapper.builder().build());
        jdbc.update("INSERT INTO schools (id) VALUES (101), (202)");
    }

    @Test
    void duplicateEventKeyCreatesOneRequestAndMessageAndClaimsAreFairAcrossSchools() {
        EnqueueMessage schoolOne = message(301L);
        EnqueueMessage schoolTwo = message(302L);
        long first = transactions.execute(status -> outbox.enqueue(101L, "PAYMENT_RECEIVED",
                "receipt:mysql-test", "{\"paymentId\":9001}", List.of(schoolOne)));
        long replay = transactions.execute(status -> outbox.enqueue(101L, "PAYMENT_RECEIVED",
                "receipt:mysql-test", "{\"paymentId\":9001}", List.of(schoolOne)));
        long otherSchool = transactions.execute(status -> outbox.enqueue(202L, "PAYMENT_RECEIVED",
                "receipt:mysql-test", "{\"paymentId\":9002}", List.of(schoolTwo)));

        assertThat(replay).isEqualTo(first);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM notification_requests
                WHERE school_id = ? AND event_key = ?
                """, Integer.class, 101L, "receipt:mysql-test")).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM notification_messages
                WHERE school_id = ? AND request_id = ?
                """, Integer.class, 101L, first)).isEqualTo(1);

        List<NotificationOutboxStore.DeliveryWorkItem> claimed = transactions.execute(status ->
                store.claimDue(2, java.time.Instant.now(), java.time.Instant.now().plusSeconds(60)));

        assertThat(claimed).hasSize(2);
        assertThat(claimed).extracting(NotificationOutboxStore.DeliveryWorkItem::schoolId)
                .containsExactlyInAnyOrder(101L, 202L);
        assertThat(otherSchool).isNotEqualTo(first);
    }

    private static EnqueueMessage message(Long recipientId) {
        return new EnqueueMessage("PARENT", recipientId, NotificationChannel.EMAIL,
                "m***@example.test", "encrypted-value", "EN", "Payment receipt",
                "Receipt available.", false, "ESSENTIAL", 0, null, null);
    }
}
