-- Add missing BaseEntity audit columns to timetable_audit table
-- TimetableAudit extends BaseEntity, which requires created_at and updated_at

ALTER TABLE timetable_audit
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_at DATETIME(6) NULL;
