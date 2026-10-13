-- Migration to add enterprise school management fields and enabled modules table

ALTER TABLE schools
    ADD COLUMN website_url VARCHAR(255),
    ADD COLUMN logo_url VARCHAR(255),
    ADD COLUMN address_line_1 VARCHAR(255),
    ADD COLUMN address_line_2 VARCHAR(255),
    ADD COLUMN city VARCHAR(100),
    ADD COLUMN state VARCHAR(100),
    ADD COLUMN country VARCHAR(100),
    ADD COLUMN postal_code VARCHAR(20),
    ADD COLUMN timezone VARCHAR(50) NOT NULL DEFAULT 'UTC',
    ADD COLUMN currency VARCHAR(10) NOT NULL DEFAULT 'USD',
    ADD COLUMN subscription_tier VARCHAR(50) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN subscription_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN subscription_start_date DATE,
    ADD COLUMN subscription_end_date DATE,
    ADD COLUMN max_students INT,
    ADD COLUMN max_teachers INT;

CREATE TABLE school_enabled_modules (
    school_id BIGINT NOT NULL,
    module_name VARCHAR(50) NOT NULL,
    PRIMARY KEY (school_id, module_name),
    CONSTRAINT fk_school_modules_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE
);
