-- V50: Idempotency keys table for duplicate request protection.
-- Scoped UNIQUE (school_id, user_id, endpoint, idempotency_key).
-- Stores a SHA-256 request fingerprint, status, response data, and expiry.
-- No personal data or secrets are stored.

CREATE TABLE idempotency_keys (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    endpoint VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    status ENUM('IN_PROGRESS', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'IN_PROGRESS',
    response_status INT,
    response_body TEXT,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_idempotency_school_user_endpoint_key (school_id, user_id, endpoint, idempotency_key),
    INDEX idx_idempotency_expires (expires_at)
) ENGINE=InnoDB;
