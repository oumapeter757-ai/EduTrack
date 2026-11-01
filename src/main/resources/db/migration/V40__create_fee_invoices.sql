-- V40: Create fee_invoices and fee_invoice_lines tables
-- Invoices are generated from fee structures for individual students

CREATE TABLE IF NOT EXISTS fee_invoices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    term_id BIGINT NOT NULL,
    structure_id BIGINT NOT NULL,
    invoice_number VARCHAR(50) NOT NULL,
    total DECIMAL(12, 2) NOT NULL,
    paid DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(10) NOT NULL DEFAULT 'KES',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    issued_on DATE NOT NULL,
    due_on DATE NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL,
    CONSTRAINT fk_fee_invoice_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_fee_invoice_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_fee_invoice_term FOREIGN KEY (term_id) REFERENCES terms(id),
    CONSTRAINT fk_fee_invoice_structure FOREIGN KEY (structure_id) REFERENCES fee_structures(id),
    CONSTRAINT uk_fee_invoice_school_number UNIQUE (school_id, invoice_number),
    CONSTRAINT uk_fee_invoice_student_term_structure UNIQUE (school_id, student_id, term_id, structure_id),
    INDEX idx_fee_invoice_school (school_id),
    INDEX idx_fee_invoice_student (student_id),
    INDEX idx_fee_invoice_status (status),
    INDEX idx_fee_invoice_term (term_id)
);

CREATE TABLE IF NOT EXISTS fee_invoice_lines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL,
    CONSTRAINT fk_fee_invoice_line_invoice FOREIGN KEY (invoice_id) REFERENCES fee_invoices(id) ON DELETE CASCADE,
    INDEX idx_fee_invoice_line_invoice (invoice_id)
);
