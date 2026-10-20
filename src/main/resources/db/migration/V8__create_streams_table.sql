CREATE TABLE streams (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    class_level_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(20) NOT NULL,
    capacity INT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_stream_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE,
    CONSTRAINT fk_stream_class_level FOREIGN KEY (class_level_id) REFERENCES class_levels(id) ON DELETE CASCADE,
    CONSTRAINT uk_stream_class_level_code UNIQUE (class_level_id, code)
);

CREATE INDEX idx_stream_school ON streams(school_id);
CREATE INDEX idx_stream_class_level ON streams(class_level_id);
