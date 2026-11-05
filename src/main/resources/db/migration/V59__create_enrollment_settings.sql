CREATE TABLE enrollment_settings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    school_id BIGINT NOT NULL,
    suggested_min_average DECIMAL(8,3) NULL,
    block_on_unpaid_fees BOOLEAN NOT NULL DEFAULT FALSE,
    block_on_open_library_loans BOOLEAN NOT NULL DEFAULT FALSE,
    require_closed_term BOOLEAN NOT NULL DEFAULT TRUE,
    undo_window_days INT NOT NULL DEFAULT 7,
    alumni_login_days INT NOT NULL DEFAULT 365,
    batch_size INT NOT NULL DEFAULT 100,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_enrollment_settings_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT uk_enrollment_settings_school UNIQUE (school_id),
    CONSTRAINT chk_enrollment_settings_undo CHECK (undo_window_days BETWEEN 0 AND 365),
    CONSTRAINT chk_enrollment_settings_alumni CHECK (alumni_login_days BETWEEN 0 AND 3650),
    CONSTRAINT chk_enrollment_settings_batch CHECK (batch_size BETWEEN 1 AND 500)
);
