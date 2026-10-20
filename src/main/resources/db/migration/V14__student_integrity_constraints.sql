-- Conflicts are deliberately recorded before constraints are added.  If this
-- migration fails, resolve the rows in this report table and rerun Flyway;
-- no rows are deleted or silently selected as winners.
CREATE TABLE IF NOT EXISTS migration_student_constraint_conflicts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conflict_type VARCHAR(40) NOT NULL,
    user_id BIGINT NULL,
    school_id BIGINT NULL,
    email VARCHAR(255) NULL,
    student_ids VARCHAR(2000) NOT NULL,
    detected_at DATETIME(6) NOT NULL
);

INSERT INTO migration_student_constraint_conflicts
    (conflict_type, user_id, school_id, email, student_ids, detected_at)
SELECT 'USER_ID', user_id, NULL, NULL,
       GROUP_CONCAT(id ORDER BY id), CURRENT_TIMESTAMP(6)
FROM students
WHERE user_id IS NOT NULL
GROUP BY user_id
HAVING COUNT(*) > 1;

INSERT INTO migration_student_constraint_conflicts
    (conflict_type, user_id, school_id, email, student_ids, detected_at)
SELECT 'SCHOOL_EMAIL', NULL, school_id, email,
       GROUP_CONCAT(id ORDER BY id), CURRENT_TIMESTAMP(6)
FROM students
WHERE email IS NOT NULL AND email <> ''
GROUP BY school_id, email
HAVING COUNT(*) > 1;

UPDATE students SET active = (status = 'ACTIVE');

ALTER TABLE students ADD CONSTRAINT uk_student_user_id UNIQUE (user_id);
ALTER TABLE students ADD CONSTRAINT uk_student_school_email UNIQUE (school_id, email);
ALTER TABLE schools ADD CONSTRAINT chk_school_max_students_positive
    CHECK (max_students IS NULL OR max_students > 0);
ALTER TABLE schools ADD CONSTRAINT chk_school_max_teachers_positive
    CHECK (max_teachers IS NULL OR max_teachers > 0);
