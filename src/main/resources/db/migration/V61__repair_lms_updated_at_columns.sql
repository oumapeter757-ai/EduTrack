SET @lms_updated_at_tables = 'lms_attachment,lms_topic,lms_resource,lms_submission,lms_course,lms_assignment,lms_announcement';

SET @lms_updated_at_table = 'lms_attachment';
SET @lms_updated_at_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = @lms_updated_at_table AND column_name = 'updated_at') = 0,
    CONCAT('ALTER TABLE ', @lms_updated_at_table, ' ADD COLUMN updated_at TIMESTAMP NULL'),
    'SELECT 1'
);
PREPARE lms_updated_at_stmt FROM @lms_updated_at_sql;
EXECUTE lms_updated_at_stmt;
DEALLOCATE PREPARE lms_updated_at_stmt;

SET @lms_updated_at_table = 'lms_topic';
SET @lms_updated_at_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = @lms_updated_at_table AND column_name = 'updated_at') = 0, CONCAT('ALTER TABLE ', @lms_updated_at_table, ' ADD COLUMN updated_at TIMESTAMP NULL'), 'SELECT 1');
PREPARE lms_updated_at_stmt FROM @lms_updated_at_sql; EXECUTE lms_updated_at_stmt; DEALLOCATE PREPARE lms_updated_at_stmt;

SET @lms_updated_at_table = 'lms_resource';
SET @lms_updated_at_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = @lms_updated_at_table AND column_name = 'updated_at') = 0, CONCAT('ALTER TABLE ', @lms_updated_at_table, ' ADD COLUMN updated_at TIMESTAMP NULL'), 'SELECT 1');
PREPARE lms_updated_at_stmt FROM @lms_updated_at_sql; EXECUTE lms_updated_at_stmt; DEALLOCATE PREPARE lms_updated_at_stmt;

SET @lms_updated_at_table = 'lms_submission';
SET @lms_updated_at_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = @lms_updated_at_table AND column_name = 'updated_at') = 0, CONCAT('ALTER TABLE ', @lms_updated_at_table, ' ADD COLUMN updated_at TIMESTAMP NULL'), 'SELECT 1');
PREPARE lms_updated_at_stmt FROM @lms_updated_at_sql; EXECUTE lms_updated_at_stmt; DEALLOCATE PREPARE lms_updated_at_stmt;

SET @lms_updated_at_table = 'lms_course';
SET @lms_updated_at_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = @lms_updated_at_table AND column_name = 'updated_at') = 0, CONCAT('ALTER TABLE ', @lms_updated_at_table, ' ADD COLUMN updated_at TIMESTAMP NULL'), 'SELECT 1');
PREPARE lms_updated_at_stmt FROM @lms_updated_at_sql; EXECUTE lms_updated_at_stmt; DEALLOCATE PREPARE lms_updated_at_stmt;

SET @lms_updated_at_table = 'lms_assignment';
SET @lms_updated_at_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = @lms_updated_at_table AND column_name = 'updated_at') = 0, CONCAT('ALTER TABLE ', @lms_updated_at_table, ' ADD COLUMN updated_at TIMESTAMP NULL'), 'SELECT 1');
PREPARE lms_updated_at_stmt FROM @lms_updated_at_sql; EXECUTE lms_updated_at_stmt; DEALLOCATE PREPARE lms_updated_at_stmt;

SET @lms_updated_at_table = 'lms_announcement';
SET @lms_updated_at_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = @lms_updated_at_table AND column_name = 'updated_at') = 0, CONCAT('ALTER TABLE ', @lms_updated_at_table, ' ADD COLUMN updated_at TIMESTAMP NULL'), 'SELECT 1');
PREPARE lms_updated_at_stmt FROM @lms_updated_at_sql; EXECUTE lms_updated_at_stmt; DEALLOCATE PREPARE lms_updated_at_stmt;
