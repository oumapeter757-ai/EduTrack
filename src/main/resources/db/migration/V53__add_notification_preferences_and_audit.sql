CREATE TABLE notification_preferences (
    id BIGINT NOT NULL AUTO_INCREMENT,
    school_id BIGINT NOT NULL,
    recipient_type VARCHAR(16) NOT NULL,
    recipient_id BIGINT NOT NULL,
    channel VARCHAR(8) NOT NULL,
    category VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_preference UNIQUE
        (school_id, recipient_type, recipient_id, channel, category),
    CONSTRAINT fk_notification_preference_school
        FOREIGN KEY (school_id) REFERENCES schools (id) ON DELETE CASCADE,
    CONSTRAINT chk_notification_preference_channel CHECK (channel IN ('EMAIL', 'SMS')),
    CONSTRAINT chk_notification_preference_category CHECK (category IN ('OPTIONAL')),
    INDEX idx_notification_preference_recipient (school_id, recipient_type, recipient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notification_sms_usage (
    school_id BIGINT NOT NULL,
    billing_month DATE NOT NULL,
    reserved_segments INT NOT NULL DEFAULT 0,
    sent_segments INT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (school_id, billing_month),
    CONSTRAINT fk_notification_sms_usage_school
        FOREIGN KEY (school_id) REFERENCES schools (id) ON DELETE CASCADE,
    CONSTRAINT chk_notification_sms_usage_nonnegative
        CHECK (reserved_segments >= 0 AND sent_segments >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE notification_messages
    ADD COLUMN sms_quota_reserved BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE notification_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    school_id BIGINT NOT NULL,
    actor_user_id BIGINT NULL,
    action VARCHAR(48) NOT NULL,
    recipient_type VARCHAR(16) NULL,
    recipient_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_audit_school
        FOREIGN KEY (school_id) REFERENCES schools (id) ON DELETE CASCADE,
    INDEX idx_notification_audit_school_created (school_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
