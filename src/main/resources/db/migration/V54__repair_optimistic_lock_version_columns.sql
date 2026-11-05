SET @v47_compat_marker_exists = (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'migration_v47_compat_tables'
);
SET @v47_compat_cleanup = IF(@v47_compat_marker_exists = 1,
    'SELECT COUNT(*) INTO @v47_drop_table FROM migration_v47_compat_tables WHERE table_name = ''lms_announcements''',
    'SELECT 0 INTO @v47_drop_table');
PREPARE v47_compat_stmt FROM @v47_compat_cleanup;
EXECUTE v47_compat_stmt;
DEALLOCATE PREPARE v47_compat_stmt;
SET @v47_compat_cleanup = IF(@v47_drop_table = 1, 'DROP TABLE IF EXISTS lms_announcements', 'SELECT 1');
PREPARE v47_compat_stmt FROM @v47_compat_cleanup;
EXECUTE v47_compat_stmt;
DEALLOCATE PREPARE v47_compat_stmt;

SET @v47_compat_cleanup = IF(@v47_compat_marker_exists = 1,
    'SELECT COUNT(*) INTO @v47_drop_table FROM migration_v47_compat_tables WHERE table_name = ''lms_topics''',
    'SELECT 0 INTO @v47_drop_table');
PREPARE v47_compat_stmt FROM @v47_compat_cleanup;
EXECUTE v47_compat_stmt;
DEALLOCATE PREPARE v47_compat_stmt;
SET @v47_compat_cleanup = IF(@v47_drop_table = 1, 'DROP TABLE IF EXISTS lms_topics', 'SELECT 1');
PREPARE v47_compat_stmt FROM @v47_compat_cleanup;
EXECUTE v47_compat_stmt;
DEALLOCATE PREPARE v47_compat_stmt;

SET @v47_compat_cleanup = IF(@v47_compat_marker_exists = 1,
    'SELECT COUNT(*) INTO @v47_drop_table FROM migration_v47_compat_tables WHERE table_name = ''stored_files''',
    'SELECT 0 INTO @v47_drop_table');
PREPARE v47_compat_stmt FROM @v47_compat_cleanup;
EXECUTE v47_compat_stmt;
DEALLOCATE PREPARE v47_compat_stmt;
SET @v47_compat_cleanup = IF(@v47_drop_table = 1, 'DROP TABLE IF EXISTS stored_files', 'SELECT 1');
PREPARE v47_compat_stmt FROM @v47_compat_cleanup;
EXECUTE v47_compat_stmt;
DEALLOCATE PREPARE v47_compat_stmt;

SET @v47_compat_cleanup = IF(@v47_compat_marker_exists = 1,
    'SELECT COUNT(*) INTO @v47_drop_table FROM migration_v47_compat_tables WHERE table_name = ''lms_attachments''',
    'SELECT 0 INTO @v47_drop_table');
PREPARE v47_compat_stmt FROM @v47_compat_cleanup;
EXECUTE v47_compat_stmt;
DEALLOCATE PREPARE v47_compat_stmt;
SET @v47_compat_cleanup = IF(@v47_drop_table = 1, 'DROP TABLE IF EXISTS lms_attachments', 'SELECT 1');
PREPARE v47_compat_stmt FROM @v47_compat_cleanup;
EXECUTE v47_compat_stmt;
DEALLOCATE PREPARE v47_compat_stmt;

SET @v47_compat_cleanup = IF(@v47_compat_marker_exists = 0,
    'SELECT 1',
    'DROP TABLE migration_v47_compat_tables');
PREPARE v47_compat_stmt FROM @v47_compat_cleanup;
EXECUTE v47_compat_stmt;
DEALLOCATE PREPARE v47_compat_stmt;

-- Flyway applies this migration once, so no pre-existing helper procedure is expected.
DELIMITER //
CREATE PROCEDURE ensure_version_column(IN table_name_arg VARCHAR(64))
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = table_name_arg
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = table_name_arg
          AND column_name = 'version'
    ) THEN
        SET @version_column_ddl = CONCAT(
            'ALTER TABLE `', REPLACE(table_name_arg, '`', '``'),
            '` ADD COLUMN version BIGINT NOT NULL DEFAULT 0'
        );
        PREPARE version_column_stmt FROM @version_column_ddl;
        EXECUTE version_column_stmt;
        DEALLOCATE PREPARE version_column_stmt;
    END IF;
END//
DELIMITER ;

CALL ensure_version_column('students');
CALL ensure_version_column('schools');
CALL ensure_version_column('timetable_lessons');
CALL ensure_version_column('users');
CALL ensure_version_column('staff');
CALL ensure_version_column('guardians');
CALL ensure_version_column('class_levels');
CALL ensure_version_column('streams');
CALL ensure_version_column('terms');
CALL ensure_version_column('academic_years');
CALL ensure_version_column('exam_series');
CALL ensure_version_column('subjects');
CALL ensure_version_column('periods');
CALL ensure_version_column('rooms');
CALL ensure_version_column('fee_invoices');
CALL ensure_version_column('fee_items');
CALL ensure_version_column('fee_structures');
CALL ensure_version_column('assessment_types');
CALL ensure_version_column('grade_scales');
CALL ensure_version_column('borrowing_policies');
CALL ensure_version_column('library_categories');
CALL ensure_version_column('books');
CALL ensure_version_column('lms_announcement');
CALL ensure_version_column('lms_topic');
CALL ensure_version_column('stored_file');
CALL ensure_version_column('lms_attachment');
CALL ensure_version_column('payment_invoices');
CALL ensure_version_column('payment_transactions');
CALL ensure_version_column('subscription_prices');
CALL ensure_version_column('parent_student_mappings');
CALL ensure_version_column('class_level_subjects');
CALL ensure_version_column('stream_subjects');
CALL ensure_version_column('callback_inbox');
CALL ensure_version_column('unallocated_payments');
CALL ensure_version_column('fee_payment_allocations');
CALL ensure_version_column('term_grades');
CALL ensure_version_column('subject_assessments');
CALL ensure_version_column('assessment_papers');
CALL ensure_version_column('grade_scale_ranges');

DROP PROCEDURE ensure_version_column;
