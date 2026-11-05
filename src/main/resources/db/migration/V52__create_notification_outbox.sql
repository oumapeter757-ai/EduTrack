CREATE TABLE notification_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    school_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_key VARCHAR(190) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_request_school_event_key UNIQUE (school_id, event_key),
    CONSTRAINT uk_notification_request_school_id UNIQUE (school_id, id),
    CONSTRAINT fk_notification_request_school
        FOREIGN KEY (school_id) REFERENCES schools (id) ON DELETE CASCADE,
    INDEX idx_notification_request_school_status_created (school_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notification_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    school_id BIGINT NOT NULL,
    request_id BIGINT NOT NULL,
    recipient_type VARCHAR(16) NOT NULL,
    recipient_id BIGINT NOT NULL,
    channel VARCHAR(8) NOT NULL,
    destination_masked VARCHAR(254) NOT NULL,
    destination_encrypted VARCHAR(2048) NOT NULL,
    language VARCHAR(8) NOT NULL,
    subject VARCHAR(500) NULL,
    body_rendered TEXT NOT NULL,
    body_html BOOLEAN NOT NULL DEFAULT FALSE,
    category VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    skip_reason VARCHAR(64) NULL,
    failure_code VARCHAR(64) NULL,
    provider VARCHAR(32) NULL,
    provider_message_id VARCHAR(190) NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NULL,
    claimed_until DATETIME(6) NULL,
    segments INT NOT NULL DEFAULT 0,
    estimated_cost DECIMAL(12,4) NULL,
    scheduled_for DATETIME(6) NULL,
    sent_at DATETIME(6) NULL,
    delivered_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_message_recipient_channel
        UNIQUE (request_id, recipient_type, recipient_id, channel),
    CONSTRAINT fk_notification_message_school
        FOREIGN KEY (school_id) REFERENCES schools (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_message_request_school
        FOREIGN KEY (school_id, request_id)
        REFERENCES notification_requests (school_id, id) ON DELETE CASCADE,
    CONSTRAINT chk_notification_message_channel CHECK (channel IN ('EMAIL', 'SMS')),
    CONSTRAINT chk_notification_message_status CHECK
        (status IN ('PENDING', 'SCHEDULED', 'CLAIMED', 'SENDING', 'SENT', 'DELIVERED', 'FAILED', 'DEAD', 'SKIPPED', 'SUPPRESSED')),
    INDEX idx_notification_message_school_queue
        (school_id, status, next_attempt_at, scheduled_for, claimed_until),
    INDEX idx_notification_message_school_recipient
        (school_id, recipient_type, recipient_id),
    INDEX idx_notification_message_provider (provider, provider_message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notification_worker_cursor (
    id TINYINT NOT NULL,
    last_school_id BIGINT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT chk_notification_worker_cursor_singleton CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO notification_worker_cursor (id, last_school_id) VALUES (1, NULL);
