-- Fix day_of_week column type: SMALLINT -> INTEGER to match Hibernate Integer mapping
-- TimetableLesson.dayOfWeek is declared as Integer, which Hibernate maps to INTEGER (Types#INTEGER)

ALTER TABLE timetable_lessons
    MODIFY COLUMN day_of_week INTEGER NOT NULL;
