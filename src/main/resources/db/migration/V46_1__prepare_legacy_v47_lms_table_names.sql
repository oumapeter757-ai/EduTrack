CREATE TABLE IF NOT EXISTS migration_v47_compat_tables (
    table_name VARCHAR(64) NOT NULL PRIMARY KEY
) ENGINE=InnoDB;

SET @v47_lms_table_exists = (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'lms_announcements'
);
SET @v47_lms_ddl = IF(@v47_lms_table_exists = 0,
    'CREATE TABLE lms_announcements (id BIGINT NOT NULL PRIMARY KEY) ENGINE=InnoDB',
    'SELECT 1');
INSERT IGNORE INTO migration_v47_compat_tables (table_name)
SELECT 'lms_announcements' WHERE @v47_lms_table_exists = 0;
PREPARE v47_lms_stmt FROM @v47_lms_ddl;
EXECUTE v47_lms_stmt;
DEALLOCATE PREPARE v47_lms_stmt;

SET @v47_lms_table_exists = (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'lms_topics'
);
SET @v47_lms_ddl = IF(@v47_lms_table_exists = 0,
    'CREATE TABLE lms_topics (id BIGINT NOT NULL PRIMARY KEY) ENGINE=InnoDB',
    'SELECT 1');
INSERT IGNORE INTO migration_v47_compat_tables (table_name)
SELECT 'lms_topics' WHERE @v47_lms_table_exists = 0;
PREPARE v47_lms_stmt FROM @v47_lms_ddl;
EXECUTE v47_lms_stmt;
DEALLOCATE PREPARE v47_lms_stmt;

SET @v47_lms_table_exists = (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'stored_files'
);
SET @v47_lms_ddl = IF(@v47_lms_table_exists = 0,
    'CREATE TABLE stored_files (id BIGINT NOT NULL PRIMARY KEY) ENGINE=InnoDB',
    'SELECT 1');
INSERT IGNORE INTO migration_v47_compat_tables (table_name)
SELECT 'stored_files' WHERE @v47_lms_table_exists = 0;
PREPARE v47_lms_stmt FROM @v47_lms_ddl;
EXECUTE v47_lms_stmt;
DEALLOCATE PREPARE v47_lms_stmt;

SET @v47_lms_table_exists = (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'lms_attachments'
);
SET @v47_lms_ddl = IF(@v47_lms_table_exists = 0,
    'CREATE TABLE lms_attachments (id BIGINT NOT NULL PRIMARY KEY) ENGINE=InnoDB',
    'SELECT 1');
INSERT IGNORE INTO migration_v47_compat_tables (table_name)
SELECT 'lms_attachments' WHERE @v47_lms_table_exists = 0;
PREPARE v47_lms_stmt FROM @v47_lms_ddl;
EXECUTE v47_lms_stmt;
DEALLOCATE PREPARE v47_lms_stmt;
