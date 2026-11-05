package com.peterscode.edutrack.concurrency;

import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.enums.SubscriptionStatus;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.entity.User;
import com.peterscode.edutrack.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base class for concurrency tests.
 * <p>
 * Uses Testcontainers MySQL 9.7 with real Flyway migrations.
 * No @Transactional on test methods — real commits are required to expose concurrency bugs.
 * </p>
 */
@SpringBootTest
@Testcontainers
public abstract class ConcurrencyTestBase {

    @Container
    protected static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:9.7")
            .withDatabaseName("edutrack_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(false);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        // Disable Redis for concurrency tests
        registry.add("edutrack.cache.enabled", () -> "false");
        // Disable mail
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> "587");
        registry.add("spring.mail.username", () -> "");
        registry.add("spring.mail.password", () -> "");
        // Storage
        registry.add("edutrack.storage.base-path", () -> "/tmp/edutrack-test-storage");
        // JWT secret (dummy for tests)
        registry.add("application.security.jwt.secret-key", () -> "test-secret-key-that-is-long-enough-for-jws-signing-1234567890");
        // Mpesa
        registry.add("mpesa.till-number", () -> "123456");
        registry.add("mpesa.short-code", () -> "123456");
        registry.add("mpesa.passkey", () -> "test-passkey");
        registry.add("mpesa.environment", () -> "test");
        registry.add("mpesa.callback-url", () -> "http://localhost:8080/api/v1/fees/callback");
        // Bootstrap token for signup tests
        registry.add("edutrack.bootstrap.token", () -> "test-bootstrap-token");
    }

    @Autowired
    protected SchoolRepository schoolRepository;

    @Autowired
    protected UserRepository userRepository;

    protected Long schoolId;
    protected Long adminUserId;

    @BeforeEach
    void setUp() {
        // Create a test school
        School school = School.builder()
                .name("Concurrency Test School")
                .code("CONC-" + System.currentTimeMillis())
                .subdomain("conc-" + System.currentTimeMillis())
                .phone("+254700000000")
                .email("test@concurrency.edu")
                .city("Nairobi")
                .country("Kenya")
                .timezone("Africa/Nairobi")
                .currency("KES")
                .subscriptionTier(SubscriptionTier.STANDARD)
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .enabledModules(Set.of(
                        ServiceModule.STUDENT_MANAGEMENT,
                        ServiceModule.ATTENDANCE,
                        ServiceModule.GRADING,
                        ServiceModule.TIMETABLE,
                        ServiceModule.EXAMINATION,
                        ServiceModule.LIBRARY,
                        ServiceModule.BILLING,
                        ServiceModule.LMS
                ))
                .maxStudents(100)
                .maxTeachers(20)
                .active(true)
                .build();
        school = schoolRepository.save(school);
        schoolId = school.getId();

        // Create a SCHOOL_ADMIN user
        User admin = User.builder()
                .firstName("Admin")
                .lastName("Test")
                .username("admin-" + schoolId)
                .email("admin-" + schoolId + "@test.edu")
                .password("Password123!")
                .role(Role.SCHOOL_ADMIN)
                .schoolId(schoolId)
                .active(true)
                .build();
        admin = userRepository.save(admin);
        adminUserId = admin.getId();
    }

    @AfterEach
    void tearDown() {
        // Clean up test data
        if (schoolId != null) {
            List<User> users = userRepository.findAll().stream()
                    .filter(u -> schoolId.equals(u.getSchoolId()))
                    .toList();
            if (!users.isEmpty()) {
                userRepository.deleteAll(users);
            }
            if (schoolRepository.existsById(schoolId)) {
                schoolRepository.deleteById(schoolId);
            }
        }
    }

    /**
     * Run N threads simultaneously using a CyclicBarrier, then collect results.
     */
    protected <T> TestResult<T> runConcurrent(int threadCount, ConcurrentTask<T> task, long timeoutSeconds)
            throws InterruptedException, java.util.concurrent.ExecutionException {
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch done = new CountDownLatch(threadCount);

        T[] results = (T[]) new Object[threadCount];
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        try {
            Future<?>[] futures = new Future[threadCount];
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                futures[i] = executor.submit(() -> {
                    try {
                        barrier.await(5, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new RuntimeException("Barrier failed", e);
                    }
                    try {
                        T result = task.execute();
                        results[index] = result;
                        if (result != null) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        if (isConflictException(e)) {
                            conflictCount.incrementAndGet();
                        } else {
                            errorCount.incrementAndGet();
                            results[index] = (T) e; // store exception for inspection
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }

            // Wait for all to complete
            boolean allDone = done.await(timeoutSeconds, TimeUnit.SECONDS);
            if (!allDone) {
                for (Future<?> f : futures) {
                    f.cancel(true);
                }
                throw new RuntimeException("Concurrency test timed out after " + timeoutSeconds + "s");
            }

            return new TestResult<>(
                    successCount.get(),
                    conflictCount.get(),
                    errorCount.get(),
                    results
            );
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /**
     * Check if an exception is a conflict-type exception (409).
     */
    protected boolean isConflictException(Throwable t) {
        if (t == null) return false;
        String msg = t.getMessage() != null ? t.getMessage().toLowerCase() : "";
        String cls = t.getClass().getSimpleName().toLowerCase();
        return msg.contains("conflict") || msg.contains("duplicate") ||
                msg.contains("constraint") || msg.contains("limit reached") ||
                cls.contains("conflict") || cls.contains("duplicate") ||
                cls.contains("integrity") || cls.contains("optimistic") ||
                cls.contains("planlimit");
    }

    /**
     * Functional interface for a concurrent task.
     */
    @FunctionalInterface
    protected interface ConcurrentTask<T> {
        T execute();
    }

    /**
     * Result of a concurrent test execution.
     */
    protected static class TestResult<T> {
        public final int successCount;
        public final int conflictCount;
        public final int errorCount;
        public final T[] results;

        TestResult(int successCount, int conflictCount, int errorCount, T[] results) {
            this.successCount = successCount;
            this.conflictCount = conflictCount;
            this.errorCount = errorCount;
            this.results = results;
        }
    }
}
