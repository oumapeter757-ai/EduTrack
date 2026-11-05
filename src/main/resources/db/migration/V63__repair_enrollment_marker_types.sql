ALTER TABLE student_enrollments
    MODIFY COLUMN current_marker INT NULL,
    MODIFY COLUMN non_voided_marker INT
        GENERATED ALWAYS AS (
            CASE WHEN status <> 'VOIDED' THEN 1 ELSE NULL END
        ) STORED;
