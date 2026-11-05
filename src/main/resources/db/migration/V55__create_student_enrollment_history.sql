CREATE TABLE student_enrollments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    school_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    academic_year_id BIGINT NULL,
    class_level_id BIGINT NULL,
    stream_id BIGINT NULL,
    status VARCHAR(30) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NULL,
    outcome_reason VARCHAR(500) NULL,
    source VARCHAR(30) NOT NULL,
    promotion_run_id BIGINT NULL,
    current_marker TINYINT NULL,
    non_voided_marker TINYINT GENERATED ALWAYS AS (
        CASE WHEN status <> 'VOIDED' THEN 1 ELSE NULL END
    ) STORED,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_enrollment_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_enrollment_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_enrollment_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id),
    CONSTRAINT fk_enrollment_class FOREIGN KEY (class_level_id) REFERENCES class_levels(id),
    CONSTRAINT fk_enrollment_stream FOREIGN KEY (stream_id) REFERENCES streams(id),
    CONSTRAINT chk_enrollment_current_marker CHECK (current_marker IS NULL OR current_marker = 1),
    CONSTRAINT uk_enrollment_current UNIQUE (school_id, student_id, current_marker),
    CONSTRAINT uk_enrollment_year UNIQUE (school_id, student_id, academic_year_id, non_voided_marker)
);

CREATE INDEX idx_enrollment_school_student ON student_enrollments(school_id, student_id);
CREATE INDEX idx_enrollment_school_year ON student_enrollments(school_id, academic_year_id);
CREATE INDEX idx_enrollment_school_class ON student_enrollments(school_id, class_level_id);

INSERT INTO student_enrollments (
    school_id, student_id, academic_year_id, class_level_id, stream_id,
    status, start_date, source, current_marker, version, created_at
)
SELECT s.school_id, s.id, COALESCE(s.academic_year_id, ay.id), s.class_level_id, s.stream_id,
       CASE s.status
           WHEN 'ACTIVE' THEN 'ENROLLED'
           WHEN 'GRADUATED' THEN 'GRADUATED'
           WHEN 'TRANSFERRED' THEN 'TRANSFERRED'
           WHEN 'SUSPENDED' THEN 'SUSPENDED'
           WHEN 'WITHDRAWN' THEN 'WITHDRAWN'
           ELSE 'WITHDRAWN'
       END,
       COALESCE(s.admission_date, ay.start_date, CURRENT_DATE),
       'MIGRATION', 1, 0, CURRENT_TIMESTAMP
FROM students s
LEFT JOIN academic_years ay
  ON ay.school_id = s.school_id AND ay.current = TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM student_enrollments e
    WHERE e.school_id = s.school_id AND e.student_id = s.id
      AND e.non_voided_marker = 1
);
