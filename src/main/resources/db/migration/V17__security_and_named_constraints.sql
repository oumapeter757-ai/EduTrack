-- Pre-checks:
-- SELECT email, COUNT(*) FROM users GROUP BY email HAVING COUNT(*) > 1;
-- SELECT username, COUNT(*) FROM users GROUP BY username HAVING COUNT(*) > 1;
-- SELECT school_id, admission_number, COUNT(*) FROM students GROUP BY school_id, admission_number HAVING COUNT(*) > 1;
-- SELECT user_id, COUNT(*) FROM students WHERE user_id IS NOT NULL GROUP BY user_id HAVING COUNT(*) > 1;

SET @schema_name = DATABASE();
DROP PROCEDURE IF EXISTS drop_school_single_column_unique_indexes;
DELIMITER //
CREATE PROCEDURE drop_school_single_column_unique_indexes()
BEGIN
    DECLARE done BOOLEAN DEFAULT FALSE;
    DECLARE idx_name VARCHAR(64);
    DECLARE indexes_to_drop CURSOR FOR
        SELECT DISTINCT s.index_name
        FROM information_schema.statistics s
        WHERE s.table_schema = DATABASE()
          AND s.table_name = 'schools'
          AND s.non_unique = 0
          AND s.index_name <> 'PRIMARY'
          AND s.index_name NOT IN ('uk_school_code', 'uk_school_subdomain')
          AND s.index_name IN ('code', 'subdomain');
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
    OPEN indexes_to_drop;
    read_loop: LOOP
        FETCH indexes_to_drop INTO idx_name;
        IF done THEN LEAVE read_loop; END IF;
        SET @sql = CONCAT('ALTER TABLE schools DROP INDEX `', idx_name, '`');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END LOOP;
    CLOSE indexes_to_drop;
END//
DELIMITER ;
CALL drop_school_single_column_unique_indexes();
DROP PROCEDURE drop_school_single_column_unique_indexes;

SET @sql = NULL;
SELECT IF(COUNT(*) = 0,
          'ALTER TABLE schools ADD CONSTRAINT uk_school_code UNIQUE (code)',
          'SELECT 1')
INTO @sql FROM information_schema.table_constraints
WHERE constraint_schema = @schema_name AND table_name = 'schools'
  AND constraint_name = 'uk_school_code';
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = NULL;
SELECT IF(COUNT(*) = 0,
          'ALTER TABLE schools ADD CONSTRAINT uk_school_subdomain UNIQUE (subdomain)',
          'SELECT 1')
INTO @sql FROM information_schema.table_constraints
WHERE constraint_schema = @schema_name AND table_name = 'schools'
  AND constraint_name = 'uk_school_subdomain';
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = NULL;
SELECT IF(COUNT(*) = 0,
          'ALTER TABLE users ADD CONSTRAINT uk_user_email UNIQUE (email)',
          'SELECT 1')
INTO @sql FROM information_schema.table_constraints
WHERE constraint_schema = @schema_name AND table_name = 'users'
  AND constraint_name = 'uk_user_email';
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = NULL;
SELECT IF(COUNT(*) = 0,
          'ALTER TABLE users ADD CONSTRAINT uk_user_username UNIQUE (username)',
          'SELECT 1')
INTO @sql FROM information_schema.table_constraints
WHERE constraint_schema = @schema_name AND table_name = 'users'
  AND constraint_name = 'uk_user_username';
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = NULL;
SELECT IF(COUNT(*) = 0,
          'ALTER TABLE students ADD CONSTRAINT uk_student_school_admission UNIQUE (school_id, admission_number)',
          'SELECT 1')
INTO @sql FROM information_schema.table_constraints
WHERE constraint_schema = @schema_name AND table_name = 'students'
  AND constraint_name = 'uk_student_school_admission';
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = NULL;
SELECT IF(COUNT(*) = 0,
          'ALTER TABLE students ADD CONSTRAINT uk_student_user_id UNIQUE (user_id)',
          'SELECT 1')
INTO @sql FROM information_schema.table_constraints
WHERE constraint_schema = @schema_name AND table_name = 'students'
  AND constraint_name = 'uk_student_user_id';
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE edutrack_bootstrap_lock (
    id BIGINT NOT NULL PRIMARY KEY,
    CONSTRAINT chk_bootstrap_lock_singleton CHECK (id = 1)
);
INSERT INTO edutrack_bootstrap_lock (id) VALUES (1);
