CREATE TABLE student_reference_backfill_conflicts (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id BIGINT NOT NULL,
 reference_type VARCHAR(30) NOT NULL, legacy_value VARCHAR(255),
 detected_at DATETIME(6) NOT NULL
);
ALTER TABLE students ADD COLUMN class_level_id BIGINT NULL;
ALTER TABLE students ADD COLUMN stream_id BIGINT NULL;
ALTER TABLE students ADD COLUMN academic_year_id BIGINT NULL;
INSERT INTO student_reference_backfill_conflicts(student_id,reference_type,legacy_value,detected_at)
SELECT s.id,'CLASS_LEVEL',s.grade_level,CURRENT_TIMESTAMP(6) FROM students s
LEFT JOIN class_levels c ON c.school_id=s.school_id AND LOWER(c.name)=LOWER(s.grade_level)
WHERE s.grade_level IS NOT NULL AND c.id IS NULL;
INSERT INTO student_reference_backfill_conflicts(student_id,reference_type,legacy_value,detected_at)
SELECT s.id,'ACADEMIC_YEAR',s.academic_year,CURRENT_TIMESTAMP(6) FROM students s
LEFT JOIN academic_years a ON a.school_id=s.school_id AND LOWER(a.name)=LOWER(s.academic_year)
WHERE s.academic_year IS NOT NULL AND a.id IS NULL;
INSERT INTO student_reference_backfill_conflicts(student_id,reference_type,legacy_value,detected_at)
SELECT s.id,'STREAM',s.section,CURRENT_TIMESTAMP(6) FROM students s
LEFT JOIN streams st ON st.school_id=s.school_id AND LOWER(st.name)=LOWER(s.section)
WHERE s.section IS NOT NULL AND st.id IS NULL;
UPDATE students s JOIN class_levels c ON c.school_id=s.school_id AND LOWER(c.name)=LOWER(s.grade_level)
SET s.class_level_id=c.id WHERE s.class_level_id IS NULL;
UPDATE students s JOIN academic_years a ON a.school_id=s.school_id AND LOWER(a.name)=LOWER(s.academic_year)
SET s.academic_year_id=a.id WHERE s.academic_year_id IS NULL;
UPDATE students s JOIN streams st ON st.school_id=s.school_id AND LOWER(st.name)=LOWER(s.section)
SET s.stream_id=st.id WHERE s.stream_id IS NULL AND (s.class_level_id IS NULL OR st.class_level_id=s.class_level_id);
ALTER TABLE students ADD CONSTRAINT fk_student_class_level FOREIGN KEY (class_level_id) REFERENCES class_levels(id);
ALTER TABLE students ADD CONSTRAINT fk_student_stream FOREIGN KEY (stream_id) REFERENCES streams(id);
ALTER TABLE students ADD CONSTRAINT fk_student_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id);
CREATE TABLE student_audit (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id BIGINT NOT NULL, school_id BIGINT NOT NULL,
 action VARCHAR(40) NOT NULL, actor_id BIGINT NULL, old_value TEXT, new_value TEXT,
 occurred_at DATETIME(6) NOT NULL, INDEX idx_student_audit_student(student_id),
 CONSTRAINT fk_student_audit_student FOREIGN KEY (student_id) REFERENCES students(id)
);
