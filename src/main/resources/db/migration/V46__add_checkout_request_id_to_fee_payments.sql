-- V46: Add checkout_request_id to fee_payments for STK Push tracking

ALTER TABLE fee_payments
    ADD COLUMN checkout_request_id VARCHAR(100);

CREATE INDEX idx_fee_payment_checkout ON fee_payments(checkout_request_id);
