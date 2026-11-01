-- V45: Create unallocated_payments table for C2B payments that couldn't be auto-matched

CREATE TABLE IF NOT EXISTS unallocated_payments (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id           BIGINT        NOT NULL,
    amount              DECIMAL(12,2) NOT NULL,
    currency            VARCHAR(10)   NOT NULL DEFAULT 'KES',
    channel             VARCHAR(20)   NOT NULL,
    provider_receipt    VARCHAR(100),
    account_reference   VARCHAR(100),
    payer_phone_masked  VARCHAR(20),
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    matched_student_id  BIGINT,
    matched_at          TIMESTAMP,
    reason              VARCHAR(500),
    resolved_by         BIGINT,
    resolved_at         TIMESTAMP,
    created_at          TIMESTAMP     NULL,
    updated_at          TIMESTAMP     NULL,

    CONSTRAINT fk_unallocated_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_unallocated_student FOREIGN KEY (matched_student_id) REFERENCES students(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_unallocated_school ON unallocated_payments(school_id);
CREATE INDEX idx_unallocated_status ON unallocated_payments(status);
CREATE INDEX idx_unallocated_provider ON unallocated_payments(provider_receipt);
