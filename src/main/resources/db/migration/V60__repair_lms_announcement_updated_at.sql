SET @lms_announcement_updated_at_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'lms_announcement'
      AND column_name = 'updated_at'
);

SET @lms_announcement_updated_at_sql = IF(
    @lms_announcement_updated_at_exists = 0,
    'ALTER TABLE lms_announcement ADD COLUMN updated_at TIMESTAMP NULL',
    'SELECT 1'
);

PREPARE lms_announcement_updated_at_stmt FROM @lms_announcement_updated_at_sql;
EXECUTE lms_announcement_updated_at_stmt;
DEALLOCATE PREPARE lms_announcement_updated_at_stmt;
