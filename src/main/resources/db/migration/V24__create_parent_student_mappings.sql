CREATE TABLE parent_student_mappings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    parent_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    relationship VARCHAR(50) NULL,
    primary_flag BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    linked_by BIGINT NOT NULL,
    linked_at DATETIME(6) NOT NULL,
    unlinked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,

    CONSTRAINT uk_parent_student_mapping UNIQUE (school_id, parent_id, student_id),
    CONSTRAINT fk_parent_mapping_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE,
    CONSTRAINT fk_parent_mapping_parent FOREIGN KEY (parent_id) REFERENCES guardians(id) ON DELETE CASCADE,
    CONSTRAINT fk_parent_mapping_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_parent_mapping_linked_by FOREIGN KEY (linked_by) REFERENCES users(id),
    CONSTRAINT chk_parent_mapping_active CHECK (active IN (TRUE, FALSE)),
    CONSTRAINT chk_parent_mapping_primary CHECK (primary_flag IN (TRUE, FALSE)),

    INDEX idx_parent_mapping_school (school_id),
    INDEX idx_parent_mapping_parent (parent_id),
    INDEX idx_parent_mapping_student (student_id),
    INDEX idx_parent_mapping_school_parent_active (school_id, parent_id, active),
    INDEX idx_parent_mapping_school_student_active (school_id, student_id, active)
);

-- Add class_teacher_id to streams for T2: class teacher overall summary
ALTER TABLE streams ADD COLUMN class_teacher_id BIGINT NULL;
CREATE INDEX idx_stream_class_teacher ON streams(class_teacher_id);
