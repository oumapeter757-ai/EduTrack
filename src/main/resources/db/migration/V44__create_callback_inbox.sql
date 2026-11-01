-- V44: Create callback_inbox table for durable Mpesa callback storage

CREATE TABLE IF NOT EXISTS callback_inbox (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id           BIGINT        NOT NULL,
    channel             VARCHAR(20)   NOT NULL,
    provider_id         VARCHAR(100)  NOT NULL,
    minimal_payload     JSON,
    payer_phone_masked  VARCHAR(20),
    received_at         TIMESTAMP     NOT NULL,
    processed           BOOLEAN       NOT NULL DEFAULT FALSE,
    processing_result   VARCHAR(500),
    retry_count         INT           NOT NULL DEFAULT 0,
    created_at          TIMESTAMP     NULL,
    updated_at          TIMESTAMP     NULL,

    CONSTRAINT fk_callback_inbox_school FOREIGN KEY (school_id) REFERENCES schools(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_callback_inbox_school_channel ON callback_inbox(school_id, channel);
CREATE INDEX idx_callback_inbox_provider ON callback_inbox(provider_id);
CREATE INDEX idx_callback_inbox_processed ON callback_inbox(processed, received_at);
CREATE INDEX idx_callback_inbox_retention ON callback_inbox(received_at);
