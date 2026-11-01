-- V42: Create fee_payments and fee_payment_allocations tables
-- Payments track all fee payments and their allocation to invoices
-- Also adds the missing FK from ledger_entries.payment_id to fee_payments

CREATE TABLE IF NOT EXISTS fee_payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    student_id BIGINT NULL,
    channel VARCHAR(20) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'KES',
    provider_receipt VARCHAR(100) NULL,
    payer_phone_masked VARCHAR(20) NULL,
    account_reference VARCHAR(100) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    failure_reason VARCHAR(500) NULL,
    initiated_by BIGINT NULL,
    receipt_number VARCHAR(50) NULL,
    confirmed_at TIMESTAMP NULL,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL,
    CONSTRAINT fk_fee_payment_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_fee_payment_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT uk_fee_payment_school_provider UNIQUE (school_id, channel, provider_receipt),
    CONSTRAINT uk_fee_payment_school_receipt UNIQUE (school_id, receipt_number),
    INDEX idx_fee_payment_school (school_id),
    INDEX idx_fee_payment_student (student_id),
    INDEX idx_fee_payment_status (status),
    INDEX idx_fee_payment_provider (provider_receipt)
);

CREATE TABLE IF NOT EXISTS fee_payment_allocations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    invoice_id BIGINT NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL,
    CONSTRAINT fk_payment_allocation_payment FOREIGN KEY (payment_id) REFERENCES fee_payments(id),
    CONSTRAINT fk_payment_allocation_invoice FOREIGN KEY (invoice_id) REFERENCES fee_invoices(id),
    CONSTRAINT uk_payment_allocation_payment_invoice UNIQUE (payment_id, invoice_id),
    INDEX idx_payment_allocation_payment (payment_id),
    INDEX idx_payment_allocation_invoice (invoice_id)
);

-- Add the FK from ledger_entries.payment_id to fee_payments (deferred from V41)
ALTER TABLE ledger_entries
    ADD CONSTRAINT fk_ledger_payment FOREIGN KEY (payment_id) REFERENCES fee_payments(id);
