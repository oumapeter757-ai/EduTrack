-- V43: Create school_payment_config table for per-school Mpesa configuration

CREATE TABLE IF NOT EXISTS school_payment_config (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id       BIGINT          NOT NULL,
    shortcode       VARCHAR(20)     NOT NULL,
    shortcode_type  VARCHAR(10)     NOT NULL DEFAULT 'PAYBILL',
    environment     VARCHAR(15)     NOT NULL DEFAULT 'SANDBOX',
    encrypted_consumer_key TEXT,
    encrypted_consumer_secret TEXT,
    encrypted_passkey TEXT,
    callback_token_hash VARCHAR(128) NOT NULL,
    enabled         BOOLEAN         NOT NULL DEFAULT FALSE,
    version         INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NULL,
    updated_at      TIMESTAMP       NULL,

    CONSTRAINT uk_payment_config_school UNIQUE (school_id),
    CONSTRAINT uk_payment_config_shortcode UNIQUE (shortcode),
    CONSTRAINT fk_payment_config_school FOREIGN KEY (school_id) REFERENCES schools(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_payment_config_shortcode ON school_payment_config(shortcode);
CREATE INDEX idx_payment_config_token_hash ON school_payment_config(callback_token_hash);
