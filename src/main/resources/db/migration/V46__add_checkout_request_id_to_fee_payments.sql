-- V46: Add checkout_request_id to fee_payments for STK Push tracking

ALTER TABLE fee_payments
    ADD COLUMN IF NOT EXISTS checkout_request_id VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_fee_payment_checkout ON fee_payments(checkout_request_id);
