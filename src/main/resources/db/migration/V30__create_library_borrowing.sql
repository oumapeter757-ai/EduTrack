-- V30: Create library borrowing, fines, and audit tables

-- Borrowing policies per school per borrower type
CREATE TABLE borrowing_policies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    borrower_type VARCHAR(20) NOT NULL,
    max_loans INTEGER NOT NULL,
    loan_days INTEGER NOT NULL,
    max_renewals INTEGER NOT NULL,
    suggested_fine_per_day DECIMAL(10,2) NOT NULL DEFAULT 0,
    max_unpaid_fines DECIMAL(10,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_policy_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT uk_policy_school_type UNIQUE (school_id, borrower_type),
    CONSTRAINT chk_borrower_type CHECK (borrower_type IN ('STUDENT', 'STAFF'))
) ENGINE=InnoDB;

-- Borrower accounts (lazy creation, upsert-safe)
CREATE TABLE library_borrower_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    borrower_type VARCHAR(20) NOT NULL,
    borrower_id BIGINT NOT NULL,
    active_loan_count INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_borrower_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT uk_borrower_account UNIQUE (school_id, borrower_type, borrower_id),
    CONSTRAINT chk_borrower_type_account CHECK (borrower_type IN ('STUDENT', 'STAFF'))
) ENGINE=InnoDB;

-- Loans
CREATE TABLE loans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    copy_id BIGINT NOT NULL,
    borrower_type VARCHAR(20) NOT NULL,
    borrower_id BIGINT NOT NULL,
    issued_by BIGINT NOT NULL,
    issued_on TIMESTAMP NOT NULL,
    due_date DATE NOT NULL,
    returned_at TIMESTAMP,
    received_by BIGINT,
    renewal_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    active_marker CHAR(36) DEFAULT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_loan_copy FOREIGN KEY (copy_id) REFERENCES book_copies(id),
    CONSTRAINT uk_loan_copy_active UNIQUE (copy_id, active_marker),
    CONSTRAINT chk_loan_status CHECK (status IN ('ACTIVE', 'RETURNED', 'LOST')),
    CONSTRAINT chk_loan_borrower_type CHECK (borrower_type IN ('STUDENT', 'STAFF'))
) ENGINE=InnoDB;

CREATE INDEX idx_loan_school_borrower ON loans(school_id, borrower_type, borrower_id);
CREATE INDEX idx_loan_school_copy ON loans(school_id, copy_id);
CREATE INDEX idx_loan_due_date ON loans(school_id, due_date);

-- Library fines
CREATE TABLE library_fines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    loan_id BIGINT NOT NULL,
    borrower_type VARCHAR(20) NOT NULL,
    borrower_id BIGINT NOT NULL,
    reason VARCHAR(20) NOT NULL,
    overdue_days INTEGER,
    suggested_amount DECIMAL(10,2),
    amount DECIMAL(10,2),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_ASSESSMENT',
    assessed_by BIGINT,
    assessed_at TIMESTAMP,
    paid_reference VARCHAR(200),
    waived_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_fine_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_fine_loan FOREIGN KEY (loan_id) REFERENCES loans(id),
    CONSTRAINT chk_fine_reason CHECK (reason IN ('OVERDUE', 'LOST', 'DAMAGED')),
    CONSTRAINT chk_fine_status CHECK (status IN ('PENDING_ASSESSMENT', 'UNPAID', 'PAID', 'WAIVED')),
    CONSTRAINT chk_fine_borrower_type CHECK (borrower_type IN ('STUDENT', 'STAFF'))
) ENGINE=InnoDB;

CREATE INDEX idx_fine_school_borrower ON library_fines(school_id, borrower_type, borrower_id);
CREATE INDEX idx_fine_school_loan ON library_fines(school_id, loan_id);

-- Library audit (append-only)
CREATE TABLE library_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id BIGINT NOT NULL,
    action VARCHAR(40) NOT NULL,
    actor_id BIGINT,
    old_value TEXT,
    new_value TEXT,
    reason VARCHAR(500),
    occurred_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_audit_school FOREIGN KEY (school_id) REFERENCES schools(id)
) ENGINE=InnoDB;

CREATE INDEX idx_audit_school_entity ON library_audit(school_id, entity_type, entity_id);
CREATE INDEX idx_audit_school_time ON library_audit(school_id, occurred_at);
CREATE INDEX idx_audit_school_actor ON library_audit(school_id, actor_id);
