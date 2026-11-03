-- V49: Add job claim columns for SKIP LOCKED multi-instance safety.
-- These columns allow scheduled jobs to claim work atomically, preventing
-- two instances from processing the same row.

-- fee_payments: STK timeout job claims PENDING payments for processing.
-- claimed_by stores a runner identifier (e.g., instance ID + timestamp).
-- claimed_until stores the lease expiry; expired leases return rows to the queue.
ALTER TABLE fee_payments
    ADD COLUMN claimed_by VARCHAR(64) NULL,
    ADD COLUMN claimed_until DATETIME NULL;

-- Index for the timeout job's SKIP LOCKED query: find unclaimed stale PENDING payments.
CREATE INDEX idx_fee_payments_timeout_claim
    ON fee_payments (status, created_at, claimed_by);

-- stored_file: LMS cleanup job claims DELETED files for processing.
ALTER TABLE stored_file
    ADD COLUMN claimed_by VARCHAR(64) NULL,
    ADD COLUMN claimed_until DATETIME NULL;

-- Index for the cleanup job's SKIP LOCKED query.
CREATE INDEX idx_stored_file_cleanup_claim
    ON stored_file (status, created_at, claimed_by);

-- payment_invoices: invoice expiry job claims PENDING expired invoices.
ALTER TABLE payment_invoices
    ADD COLUMN claimed_by VARCHAR(64) NULL,
    ADD COLUMN claimed_until DATETIME NULL;

-- Index for the expiry job's SKIP LOCKED query.
CREATE INDEX idx_payment_invoices_expire_claim
    ON payment_invoices (status, expires_at, claimed_by);
