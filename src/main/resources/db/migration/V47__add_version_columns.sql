-- V47: Add version columns to mutable entities for optimistic locking.
-- All columns are BIGINT NOT NULL DEFAULT 0, safe on large tables.
-- Entities already with @Version are excluded: StudentAttendance, ExamRegistration,
-- FeePayment, SchoolPaymentConfig, StudentFeeAccount, StudentScore, BookCopy,
-- LibraryBorrowerAccount, LibraryFine, Loan, LmsAssignment, LmsCourse, LmsResource,
-- LmsSubmission, SchoolStorageUsage, Timetable.

-- Critical: frequently edited, high concurrency risk
ALTER TABLE students ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE schools ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE timetable_lessons ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- High: edited but less frequent
ALTER TABLE staff ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE guardians ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE class_levels ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE streams ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE terms ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE academic_years ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE exam_series ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE subjects ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE periods ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE rooms ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Medium: configuration or rare edits
ALTER TABLE fee_invoices ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE fee_items ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE fee_structures ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE assessment_types ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE grade_scales ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE borrowing_policies ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE library_categories ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE books ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE lms_announcements ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE lms_topics ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE stored_files ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE lms_attachments ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payment_invoices ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payment_transactions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE subscription_prices ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE parent_student_mappings ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE class_level_subjects ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE stream_subjects ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE callback_inbox ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE unallocated_payments ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE fee_payment_allocations ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE term_grades ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE subject_assessments ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE assessment_papers ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE grade_scale_ranges ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
