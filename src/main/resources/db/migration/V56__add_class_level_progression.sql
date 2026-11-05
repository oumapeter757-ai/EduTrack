ALTER TABLE class_levels
    ADD COLUMN graduating BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN next_class_level_id BIGINT NULL;

ALTER TABLE class_levels
    ADD CONSTRAINT fk_class_level_next
    FOREIGN KEY (next_class_level_id) REFERENCES class_levels(id);

CREATE INDEX idx_class_level_next ON class_levels(next_class_level_id);
