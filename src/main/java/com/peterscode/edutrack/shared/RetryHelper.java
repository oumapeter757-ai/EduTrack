package com.peterscode.edutrack.shared;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Random;
import java.util.function.Supplier;

/**
 * Bounded retry helper for database operations that may fail due to transient
 * concurrency conflicts (deadlocks, lock timeouts, optimistic lock failures).
 * <p>
 * Wraps the WHOLE transactional call from OUTSIDE the transaction.
 * Retries only for idempotent or fully-rolled-back operations.
 * Never retry around external calls (M-Pesa, SMTP).
 * </p>
 * <p>
 * Default: 3 attempts, 100ms base delay, 1s cap, with jitter.
 * </p>
 */
public class RetryHelper {

    private final int maxAttempts;
    private final long baseDelayMs;
    private final long maxDelayMs;
    private final Random random;

    public RetryHelper() {
        this(3, 100, 1000);
    }

    public RetryHelper(int maxAttempts, long baseDelayMs, long maxDelayMs) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
        if (baseDelayMs < 0) throw new IllegalArgumentException("baseDelayMs must be >= 0");
        if (maxDelayMs < baseDelayMs) throw new IllegalArgumentException("maxDelayMs must be >= baseDelayMs");
        this.maxAttempts = maxAttempts;
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.random = new Random();
    }

    /**
     * Execute the supplier with bounded retry on retryable exceptions.
     *
     * @param action the transactional operation to execute
     * @param <T>    return type
     * @return the result of the action
     * @throws RuntimeException if all retries are exhausted
     */
    public <T> T execute(Supplier<T> action) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (Exception e) {
                if (!isRetryable(e)) {
                    throw e; // non-retryable — fail immediately
                }
                lastException = e;
                if (attempt < maxAttempts) {
                    long delay = calculateDelay(attempt);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                }
            }
        }
        throw new RetryExhaustedException(
                "Retry exhausted after " + maxAttempts + " attempts", lastException, maxAttempts);
    }

    /**
     * Execute a void action with bounded retry.
     */
    public void execute(Runnable action) {
        execute(() -> {
            action.run();
            return null;
        });
    }

    /**
     * Check if an exception is retryable (transient concurrency conflict).
     */
    public static boolean isRetryable(Throwable t) {
        if (t == null) return false;
        Class<?> clazz = t.getClass();
        return clazz == DeadlockLoserDataAccessException.class ||
                clazz == CannotAcquireLockException.class ||
                clazz == ObjectOptimisticLockingFailureException.class ||
                (clazz == DataIntegrityViolationException.class && isLockTimeout(t));
    }

    private static boolean isLockTimeout(Throwable t) {
        // MySQL error 1205 = Lock wait timeout exceeded
        String msg = t.getMessage();
        if (msg != null && msg.contains("1205")) return true;
        Throwable cause = t.getCause();
        if (cause != null) {
            String causeMsg = cause.getMessage();
            if (causeMsg != null && causeMsg.contains("1205")) return true;
        }
        return false;
    }

    private long calculateDelay(int attempt) {
        // Exponential backoff with jitter
        long exponentialDelay = baseDelayMs * (1L << (attempt - 1));
        long cappedDelay = Math.min(exponentialDelay, maxDelayMs);
        // Add jitter: ±25%
        long jitterRange = cappedDelay / 4;
        long jitter = random.nextInt((int) (jitterRange * 2 + 1)) - jitterRange;
        return Math.max(0, cappedDelay + jitter);
    }

    /**
     * Exception thrown when all retry attempts are exhausted.
     */
    public static class RetryExhaustedException extends RuntimeException {
        private final int attempts;

        public RetryExhaustedException(String message, Throwable cause, int attempts) {
            super(message, cause);
            this.attempts = attempts;
        }

        public int getAttempts() {
            return attempts;
        }
    }
}
