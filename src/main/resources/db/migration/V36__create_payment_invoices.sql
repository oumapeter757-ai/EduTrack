CREATE TABLE payment_invoices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_number VARCHAR(64) NOT NULL UNIQUE,
    school_id BIGINT NOT NULL,
    tier VARCHAR(50) NOT NULL,
    billing_period VARCHAR(50) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'KES',
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    mpesa_transaction_id VARCHAR(64),
    validated_at DATETIME(6),
    confirmed_at DATETIME(6),
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6),
    CONSTRAINT fk_payment_invoice_school FOREIGN KEY (school_id) REFERENCES schools(id)
);

CREATE INDEX idx_payment_invoice_status ON payment_invoices(status);
CREATE INDEX idx_payment_invoice_expires ON payment_invoices(expires_at);
CREATE INDEX idx_payment_invoice_school ON payment_invoices(school_id);
