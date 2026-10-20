-- Pre-checks:
-- SELECT id, role, school_id FROM users
-- WHERE NOT ((role = 'SUPER_ADMIN' AND school_id IS NULL)
--        OR (role <> 'SUPER_ADMIN' AND school_id IS NOT NULL));
-- SELECT COUNT(*) FROM students WHERE active <> (status = 'ACTIVE');
-- Existing foreign keys are present for students.school_id, class_level_id, stream_id,
-- academic_year_id, user_id, and users.school_id; no duplicate tenant FKs are added.

DROP PROCEDURE IF EXISTS validate_user_school_role;
DELIMITER //
CREATE PROCEDURE validate_user_school_role()
BEGIN
    IF EXISTS (
        SELECT 1 FROM users
        WHERE NOT ((role = 'SUPER_ADMIN' AND school_id IS NULL)
                OR (role <> 'SUPER_ADMIN' AND school_id IS NOT NULL))
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Migration aborted: users contain invalid role/school_id rows';
    END IF;
END//
DELIMITER ;
CALL validate_user_school_role();
DROP PROCEDURE validate_user_school_role;

SET @sql = NULL;
SELECT IF(COUNT(*) = 0,
          'ALTER TABLE users ADD CONSTRAINT chk_user_role_school CHECK ((role = ''SUPER_ADMIN'' AND school_id IS NULL) OR (role <> ''SUPER_ADMIN'' AND school_id IS NOT NULL))',
          'SELECT 1')
INTO @sql
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND table_name = 'users'
  AND constraint_name = 'chk_user_role_school';
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = NULL;
SELECT IF(COUNT(*) = 0,
          'CREATE INDEX idx_users_school_role_active ON users(school_id, role, active)',
          'SELECT 1')
INTO @sql
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'users'
  AND index_name = 'idx_users_school_role_active';
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Remove legacy single-column unique indexes while retaining the named constraints.
DROP PROCEDURE IF EXISTS drop_legacy_user_unique_indexes;
DELIMITER //
CREATE PROCEDURE drop_legacy_user_unique_indexes()
BEGIN
    DECLARE done BOOLEAN DEFAULT FALSE;
    DECLARE idx_name VARCHAR(64);
    DECLARE indexes_to_drop CURSOR FOR
        SELECT DISTINCT s.index_name
        FROM information_schema.statistics s
        WHERE s.table_schema = DATABASE()
          AND s.table_name = 'users'
          AND s.non_unique = 0
          AND s.index_name <> 'PRIMARY'
          AND s.index_name NOT IN ('uk_user_email', 'uk_user_username')
          AND s.index_name IN ('email', 'username');
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
    OPEN indexes_to_drop;
    read_loop: LOOP
        FETCH indexes_to_drop INTO idx_name;
        IF done THEN LEAVE read_loop; END IF;
        SET @sql = CONCAT('ALTER TABLE users DROP INDEX `', idx_name, '`');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END LOOP;
    CLOSE indexes_to_drop;
END//
DELIMITER ;
CALL drop_legacy_user_unique_indexes();
DROP PROCEDURE drop_legacy_user_unique_indexes;

-- Pre-check count is documented above; this update records the corrected source of truth.
UPDATE students
SET active = (status = 'ACTIVE')
WHERE active <> (status = 'ACTIVE');
