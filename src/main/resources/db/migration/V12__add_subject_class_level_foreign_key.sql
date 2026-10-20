ALTER TABLE subjects
    ADD CONSTRAINT fk_subject_class_level
        FOREIGN KEY (class_level_id) REFERENCES class_levels(id) ON DELETE SET NULL;
