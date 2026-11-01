CREATE TABLE payment_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    mpesa_transaction_id VARCHAR(64),
    amount DECIMAL(12, 2),
    details TEXT,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_payment_transaction_invoice FOREIGN KEY (invoice_id) REFERENCES payment_invoices(id)
);

CREATE INDEX idx_payment_transaction_invoice ON payment_transactions(invoice_id);
CREATE INDEX idx_payment_transaction_created ON payment_transactions(created_at);
