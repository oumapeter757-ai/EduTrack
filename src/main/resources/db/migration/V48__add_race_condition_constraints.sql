-- V48: Add unique constraints to prevent race conditions.
-- All constraints are safe on existing data (no duplicates expected at creation time).

-- Prevent two concurrent STK push requests from creating two PENDING payments for the same student.
-- MySQL 8 does not support partial unique indexes (WHERE clause). Instead, we use a functional
-- unique index with CASE WHEN that returns a stable value for PENDING rows and NULL otherwise.
-- Since MySQL treats NULL as distinct from all values in unique constraints, only PENDING rows
-- are enforced. We use school_id in the CASE expression (not student_id which is already in the
-- index prefix) to avoid redundancy — the combination of (school_id, student_id, stable_value)
-- ensures at most one PENDING payment per student per school.
ALTER TABLE fee_payments
    ADD UNIQUE INDEX uk_fee_payment_pending_student (
        school_id,
        student_id,
        (CASE WHEN status = 'PENDING' THEN school_id ELSE NULL END)
    );
