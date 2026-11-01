-- V38: Create fee_structures and fee_items tables
-- Fee structures define what fees are charged per class level per term

CREATE TABLE IF NOT EXISTS fee_structures (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    academic_year_id BIGINT NOT NULL,
    term_id BIGINT NOT NULL,
    class_level_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL,
    CONSTRAINT fk_fee_structure_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_fee_structure_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id),
    CONSTRAINT fk_fee_structure_term FOREIGN KEY (term_id) REFERENCES terms(id),
    CONSTRAINT fk_fee_structure_class_level FOREIGN KEY (class_level_id) REFERENCES class_levels(id),
    CONSTRAINT uk_fee_structure_school_term_class UNIQUE (school_id, term_id, class_level_id, name),
    INDEX idx_fee_structure_school (school_id),
    INDEX idx_fee_structure_term (term_id),
    INDEX idx_fee_structure_class_level (class_level_id)
);

CREATE TABLE IF NOT EXISTS fee_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    structure_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    mandatory BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL,
    CONSTRAINT fk_fee_item_structure FOREIGN KEY (structure_id) REFERENCES fee_structures(id) ON DELETE CASCADE,
    INDEX idx_fee_item_structure (structure_id)
);
