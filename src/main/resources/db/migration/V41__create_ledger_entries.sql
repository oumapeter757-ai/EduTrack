-- V41: Create ledger_entries table
-- Append-only ledger for all fee transactions. No update or delete allowed.
-- Note: payment_id FK is added in V42 after fee_payments table is created.

CREATE TABLE IF NOT EXISTS ledger_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    invoice_id BIGINT NULL,
    payment_id BIGINT NULL,
    reversal_of_entry_id BIGINT NULL,
    description VARCHAR(500) NULL,
    created_by BIGINT NULL,
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL,
    CONSTRAINT fk_ledger_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_ledger_account FOREIGN KEY (account_id) REFERENCES student_fee_accounts(id),
    CONSTRAINT fk_ledger_invoice FOREIGN KEY (invoice_id) REFERENCES fee_invoices(id),
    CONSTRAINT fk_ledger_reversal FOREIGN KEY (reversal_of_entry_id) REFERENCES ledger_entries(id),
    INDEX idx_ledger_school_account (school_id, account_id),
    INDEX idx_ledger_school_created (school_id, created_at),
    INDEX idx_ledger_invoice (invoice_id),
    INDEX idx_ledger_payment (payment_id)
);
