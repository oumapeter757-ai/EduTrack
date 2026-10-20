-- V14 logged MySQL warning 1052 because the literal 'USER_ID' was unaliased
-- while the conflict query grouped by user_id. V14 was deliberately not edited
-- because changing an applied migration would cause a Flyway checksum mismatch.

SET @affected_student_email = 0;
UPDATE students
SET email = NULL
WHERE email IS NOT NULL AND TRIM(email) = '';
SET @affected_student_email = ROW_COUNT();
SELECT 'students.email blank values normalized to NULL' AS migration_note,
       @affected_student_email AS affected_rows;

SET @affected_guardian_email = 0;
UPDATE students
SET guardian_email = NULL
WHERE guardian_email IS NOT NULL AND TRIM(guardian_email) = '';
SET @affected_guardian_email = ROW_COUNT();
SELECT 'students.guardian_email blank values normalized to NULL' AS migration_note,
       @affected_guardian_email AS affected_rows;

UPDATE students
SET middle_name = NULLIF(TRIM(middle_name), ''),
    address_line_1 = NULLIF(TRIM(address_line_1), ''),
    address_line_2 = NULLIF(TRIM(address_line_2), ''),
    city = NULLIF(TRIM(city), ''),
    state = NULLIF(TRIM(state), ''),
    country = NULLIF(TRIM(country), ''),
    postal_code = NULLIF(TRIM(postal_code), ''),
    profile_image_url = NULLIF(TRIM(profile_image_url), ''),
    grade_level = NULLIF(TRIM(grade_level), ''),
    section = NULLIF(TRIM(section), ''),
    roll_number = NULLIF(TRIM(roll_number), ''),
    academic_year = NULLIF(TRIM(academic_year), ''),
    emergency_contact_name = NULLIF(TRIM(emergency_contact_name), ''),
    emergency_contact_relationship = NULLIF(TRIM(emergency_contact_relationship), ''),
    guardian_name = NULLIF(TRIM(guardian_name), ''),
    guardian_relationship = NULLIF(TRIM(guardian_relationship), ''),
    medical_notes = NULLIF(TRIM(medical_notes), '');

DROP PROCEDURE IF EXISTS drop_student_integrity_duplicate_indexes;
DELIMITER //
CREATE PROCEDURE drop_student_integrity_duplicate_indexes()
BEGIN
    DECLARE done BOOLEAN DEFAULT FALSE;
    DECLARE drop_table VARCHAR(64);
    DECLARE idx_name VARCHAR(64);
    DECLARE indexes_to_drop CURSOR FOR
        SELECT s.table_name, s.index_name
        FROM information_schema.statistics s
        WHERE s.table_schema = DATABASE()
          AND s.non_unique = 0
          AND s.index_name <> 'PRIMARY'
          AND (
              (s.table_name = 'students' AND s.index_name NOT IN
                   ('uk_student_user_id', 'uk_student_school_admission', 'uk_student_school_email')
               AND s.index_name IN (
                   SELECT x.index_name FROM information_schema.statistics x
                   WHERE x.table_schema = DATABASE() AND x.table_name = 'students'
                   GROUP BY x.index_name
                   HAVING GROUP_CONCAT(x.column_name ORDER BY x.seq_in_index) IN
                       ('user_id', 'school_id,admission_number', 'school_id,email')
               ))
              OR (s.table_name = 'users' AND s.index_name NOT IN
                   ('uk_user_email', 'uk_user_username')
               AND s.index_name IN (
                   SELECT x.index_name FROM information_schema.statistics x
                   WHERE x.table_schema = DATABASE() AND x.table_name = 'users'
                   GROUP BY x.index_name
                   HAVING GROUP_CONCAT(x.column_name ORDER BY x.seq_in_index) IN
                       ('email', 'username')
               ))
              OR (s.table_name = 'schools' AND s.index_name NOT IN
                   ('uk_school_code', 'uk_school_subdomain')
               AND s.index_name IN (
                   SELECT x.index_name FROM information_schema.statistics x
                   WHERE x.table_schema = DATABASE() AND x.table_name = 'schools'
                   GROUP BY x.index_name
                   HAVING GROUP_CONCAT(x.column_name ORDER BY x.seq_in_index) IN
                       ('code', 'subdomain')
               ))
          )
        GROUP BY s.table_name, s.index_name;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
    OPEN indexes_to_drop;
    read_loop: LOOP
        FETCH indexes_to_drop INTO drop_table, idx_name;
        IF done THEN LEAVE read_loop; END IF;
        SET @sql = CONCAT('ALTER TABLE `', drop_table, '` DROP INDEX `', idx_name, '`');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END LOOP;
    CLOSE indexes_to_drop;
END//
DELIMITER ;
CALL drop_student_integrity_duplicate_indexes();
DROP PROCEDURE drop_student_integrity_duplicate_indexes;

DROP PROCEDURE IF EXISTS validate_student_integrity_duplicates;
DELIMITER //
CREATE PROCEDURE validate_student_integrity_duplicates()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE constraint_schema = DATABASE() AND table_name = 'students'
                     AND constraint_name = 'uk_student_user_id')
       AND EXISTS (SELECT 1 FROM students WHERE user_id IS NOT NULL GROUP BY user_id HAVING COUNT(*) > 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'Migration aborted: duplicate students.user_id; inspect duplicate student ids before retrying';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE constraint_schema = DATABASE() AND table_name = 'students'
                     AND constraint_name = 'uk_student_school_admission')
       AND EXISTS (SELECT 1 FROM students GROUP BY school_id, admission_number HAVING COUNT(*) > 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'Migration aborted: duplicate students (school_id, admission_number); inspect duplicate student ids before retrying';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE constraint_schema = DATABASE() AND table_name = 'students'
                     AND constraint_name = 'uk_student_school_email')
       AND EXISTS (SELECT 1 FROM students WHERE email IS NOT NULL GROUP BY school_id, email HAVING COUNT(*) > 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'Migration aborted: duplicate students (school_id, email); inspect duplicate student ids before retrying';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE constraint_schema = DATABASE() AND table_name = 'users'
                     AND constraint_name = 'uk_user_email')
       AND EXISTS (SELECT 1 FROM users WHERE email IS NOT NULL GROUP BY email HAVING COUNT(*) > 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'Migration aborted: duplicate users.email; inspect duplicate user ids before retrying';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE constraint_schema = DATABASE() AND table_name = 'users'
                     AND constraint_name = 'uk_user_username')
       AND EXISTS (SELECT 1 FROM users WHERE username IS NOT NULL GROUP BY username HAVING COUNT(*) > 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'Migration aborted: duplicate users.username; inspect duplicate user ids before retrying';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE constraint_schema = DATABASE() AND table_name = 'schools'
                     AND constraint_name = 'uk_school_code')
       AND EXISTS (SELECT 1 FROM schools WHERE code IS NOT NULL GROUP BY code HAVING COUNT(*) > 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'Migration aborted: duplicate schools.code; inspect duplicate school ids before retrying';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE constraint_schema = DATABASE() AND table_name = 'schools'
                     AND constraint_name = 'uk_school_subdomain')
       AND EXISTS (SELECT 1 FROM schools WHERE subdomain IS NOT NULL GROUP BY subdomain HAVING COUNT(*) > 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'Migration aborted: duplicate schools.subdomain; inspect duplicate school ids before retrying';
    END IF;
END//
DELIMITER ;
CALL validate_student_integrity_duplicates();
DROP PROCEDURE validate_student_integrity_duplicates;

SET @sql = NULL;
SELECT IF(COUNT(*) = 0,
          'ALTER TABLE students ADD CONSTRAINT uk_student_user_id UNIQUE (user_id)',
          'SELECT 1')
INTO @sql FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE() AND table_name = 'students'
  AND constraint_name = 'uk_student_user_id';
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = NULL;
SELECT IF(COUNT(*) = 0,
          'ALTER TABLE students ADD CONSTRAINT uk_student_school_admission UNIQUE (school_id, admission_number)',
          'SELECT 1')
INTO @sql FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE() AND table_name = 'students'
  AND constraint_name = 'uk_student_school_admission';
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = NULL;
SELECT IF(COUNT(*) = 0,
          'ALTER TABLE students ADD CONSTRAINT uk_student_school_email UNIQUE (school_id, email)',
          'SELECT 1')
INTO @sql FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE() AND table_name = 'students'
  AND constraint_name = 'uk_student_school_email';
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = NULL;
SELECT IF(COUNT(*) = 0,
          'ALTER TABLE users ADD CONSTRAINT uk_user_email UNIQUE (email)',
          'SELECT 1')
INTO @sql FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE() AND table_name = 'users'
  AND constraint_name = 'uk_user_email';
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = NULL;
SELECT IF(COUNT(*) = 0,
          'ALTER TABLE users ADD CONSTRAINT uk_user_username UNIQUE (username)',
          'SELECT 1')
INTO @sql FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE() AND table_name = 'users'
  AND constraint_name = 'uk_user_username';
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = NULL;
SELECT IF(COUNT(*) = 0,
          'ALTER TABLE schools ADD CONSTRAINT uk_school_code UNIQUE (code)',
          'SELECT 1')
INTO @sql FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE() AND table_name = 'schools'
  AND constraint_name = 'uk_school_code';
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = NULL;
SELECT IF(COUNT(*) = 0,
          'ALTER TABLE schools ADD CONSTRAINT uk_school_subdomain UNIQUE (subdomain)',
          'SELECT 1')
INTO @sql FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE() AND table_name = 'schools'
  AND constraint_name = 'uk_school_subdomain';
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
