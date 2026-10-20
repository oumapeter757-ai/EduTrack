-- Migration to create subjects table with multi-tenancy and audit fields

CREATE TABLE subjects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    class_level_id BIGINT,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(20) NOT NULL,
    description VARCHAR(500),
    department VARCHAR(100),
    credit_hours INT,
    is_elective BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_subject_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE,
    CONSTRAINT uk_subject_school_code UNIQUE (school_id, code)
);

CREATE INDEX idx_subject_school ON subjects(school_id);
CREATE INDEX idx_subject_school_department ON subjects(school_id, department);
CREATE INDEX idx_subject_class_level ON subjects(class_level_id);
