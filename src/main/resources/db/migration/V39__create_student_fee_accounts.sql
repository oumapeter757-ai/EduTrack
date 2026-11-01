-- V39: Create student_fee_accounts table
-- Each student has one fee account per school that tracks their balance

CREATE TABLE IF NOT EXISTS student_fee_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'KES',
    cached_balance DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL,
    CONSTRAINT fk_fee_account_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_fee_account_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT uk_fee_account_school_student UNIQUE (school_id, student_id),
    INDEX idx_fee_account_school (school_id),
    INDEX idx_fee_account_student (student_id)
);
