-- V31: LMS Storage Foundation
-- stored_file: secure file storage with validation metadata
CREATE TABLE stored_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    uploaded_by BIGINT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(127) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    scan_status VARCHAR(16) NOT NULL DEFAULT 'UNSCANNED' CHECK (scan_status IN ('UNSCANNED', 'CLEAN', 'REJECTED')),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DELETED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_stored_file_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_stored_file_school FOREIGN KEY (school_id) REFERENCES schools(id),
    INDEX idx_stored_file_school (school_id),
    INDEX idx_stored_file_status (status),
    INDEX idx_stored_file_scan_status (scan_status)
) ENGINE=InnoDB;

-- school_storage_usage: per-school storage quota tracking
CREATE TABLE school_storage_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    used_bytes BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_school_storage_usage_school UNIQUE (school_id),
    CONSTRAINT fk_school_storage_usage_school FOREIGN KEY (school_id) REFERENCES schools(id)
) ENGINE=InnoDB;

-- lms_audit: append-only audit log for all LMS actions
CREATE TABLE lms_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    actor_id BIGINT NOT NULL,
    old_value TEXT,
    new_value TEXT,
    reason VARCHAR(500),
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lms_audit_school FOREIGN KEY (school_id) REFERENCES schools(id),
    INDEX idx_lms_audit_school_entity (school_id, entity_type, entity_id),
    INDEX idx_lms_audit_school_occurred (school_id, occurred_at),
    INDEX idx_lms_audit_school_actor (school_id, actor_id)
) ENGINE=InnoDB;
