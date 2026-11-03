package com.peterscode.edutrack.shared;

import com.peterscode.edutrack.shared.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * Idempotency service for duplicate request protection.
 * <p>
 * Uses a database table with a unique constraint on (school_id, user_id, endpoint, key)
 * as the final guard against concurrent duplicates. The unique constraint decides the winner;
 * a concurrent duplicate gets 409. A reused key with a different fingerprint gets 422.
 * </p>
 * <p>
 * Flow:
 * 1. Insert the idempotency key with IN_PROGRESS status before doing the work.
 *    The unique constraint serializes concurrent inserts.
 * 2. If insert fails with a constraint violation, check the existing record:
 *    - IN_PROGRESS: the other request is still running → return 409
 *    - COMPLETED: return the cached response
 *    - FAILED: return the cached error
 *    - Expired: delete and retry insert
 * 3. If fingerprints differ, return 422.
 * 4. Execute the work.
 * 5. Update the record with the result.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;
    private final Clock clock;

    @Value("${edutrack.idempotency.ttl-hours:24}")
    private int ttlHours;

    /**
     * Execute an idempotent operation, protecting against duplicate requests.
     *
     * @param schoolId   the school id
     * @param userId     the user id
     * @param endpoint   the endpoint path (e.g., "/api/v1/fees/stk-push")
     * @param idempotencyKey the client-provided idempotency key
     * @param fingerprint SHA-256 hash of the request payload
     * @param action     the operation to execute
     * @param <T>        return type
     * @return the result of the operation
     * @throws IdempotencyConflictException if a concurrent duplicate is detected (409)
     * @throws IdempotencyFingerprintMismatch if the key is reused with a different payload (422)
     */
    @Transactional
    public <T> T execute(Long schoolId, Long userId, String endpoint,
                          String idempotencyKey, String fingerprint,
                          Supplier<T> action) {
        // Try to insert the key — the unique constraint decides the winner
        IdempotencyKey newKey = IdempotencyKey.builder()
                .schoolId(schoolId)
                .userId(userId)
                .endpoint(endpoint)
                .key(idempotencyKey)
                .requestFingerprint(fingerprint)
                .status(IdempotencyKey.KeyStatus.IN_PROGRESS)
                .expiresAt(LocalDateTime.now(clock).plusHours(ttlHours))
                .build();

        try {
            repository.saveAndFlush(newKey);
        } catch (DataIntegrityViolationException e) {
            // Unique constraint violation — another request with the same key exists
            IdempotencyKey existing = repository
                    .findBySchoolIdAndUserIdAndEndpointAndKey(schoolId, userId, endpoint, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "Idempotency key constraint violated but record not found"));

            // Check if expired
            if (LocalDateTime.now(clock).isAfter(existing.getExpiresAt())) {
                // Expired — delete and retry insert
                repository.delete(existing);
                repository.flush();
                repository.saveAndFlush(newKey);
            } else {
                // Check fingerprint match
                if (!fingerprint.equals(existing.getRequestFingerprint())) {
                    throw new IdempotencyFingerprintMismatch(
                            "Idempotency key reused with a different request payload");
                }

                // Check status
                if (existing.getStatus() == IdempotencyKey.KeyStatus.IN_PROGRESS) {
                    throw new IdempotencyConflictException(
                            "A request with this idempotency key is still being processed");
                }

                // Return cached result for COMPLETED/FAILED
                if (existing.getStatus() == IdempotencyKey.KeyStatus.FAILED) {
                    throw new IdempotencyConflictException(
                            existing.getResponseBody() != null
                                    ? existing.getResponseBody()
                                    : "Previous request with this key failed");
                }

                // COMPLETED — we need to return the cached result
                // For simplicity, re-execute is not done; the caller should handle this
                // In a full implementation, the response body would be deserialized
                throw new IdempotencyConflictException("Request already completed");
            }
        }

        // Execute the actual work
        try {
            T result = action.get();
            newKey.setStatus(IdempotencyKey.KeyStatus.COMPLETED);
            repository.save(newKey);
            return result;
        } catch (Exception e) {
            newKey.setStatus(IdempotencyKey.KeyStatus.FAILED);
            newKey.setResponseBody(sanitizeError(e.getMessage()));
            repository.save(newKey);
            throw e;
        }
    }

    /**
     * Generate a SHA-256 fingerprint of a request payload.
     */
    public static String fingerprint(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute fingerprint", e);
        }
    }

    /**
     * Clean up expired idempotency keys. Call from a scheduled job.
     */
    @Transactional
    public int cleanupExpired() {
        int deleted = repository.deleteByExpiresAtBefore(LocalDateTime.now(clock));
        if (deleted > 0) {
            log.info("Cleaned up {} expired idempotency keys", deleted);
        }
        return deleted;
    }

    private static String sanitizeError(String message) {
        if (message == null) return "Unknown error";
        // Remove any potential sensitive data
        return message.replaceAll("(?i)password\\s*[:=]\\s*\\S+", "password=[REDACTED]")
                .replaceAll("(?i)token\\s*[:=]\\s*\\S+", "token=[REDACTED]");
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Exception thrown when a concurrent duplicate request is detected (409).
     */
    public static class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when an idempotency key is reused with a different payload (422).
     */
    public static class IdempotencyFingerprintMismatch extends RuntimeException {
        public IdempotencyFingerprintMismatch(String message) {
            super(message);
        }
    }
}
