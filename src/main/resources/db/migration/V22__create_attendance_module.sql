CREATE TABLE student_attendance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    term_id BIGINT NOT NULL,
    stream_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    attendance_date DATE NOT NULL,
    attendance_type VARCHAR(16) NOT NULL,
    timetable_lesson_id BIGINT NULL,
    lesson_scope_id BIGINT GENERATED ALWAYS AS (IFNULL(timetable_lesson_id, 0)) STORED,
    status VARCHAR(16) NOT NULL,
    remarks VARCHAR(500) NULL,
    marked_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT fk_attendance_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_attendance_term FOREIGN KEY (term_id) REFERENCES terms(id),
    CONSTRAINT fk_attendance_stream FOREIGN KEY (stream_id) REFERENCES streams(id),
    CONSTRAINT fk_attendance_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_attendance_lesson FOREIGN KEY (timetable_lesson_id) REFERENCES timetable_lessons(id),
    CONSTRAINT fk_attendance_marker FOREIGN KEY (marked_by) REFERENCES users(id),
    CONSTRAINT uk_attendance_school_student_date_scope
        UNIQUE (school_id, student_id, attendance_date, lesson_scope_id),
    CONSTRAINT chk_attendance_type CHECK (attendance_type IN ('DAILY', 'PERIOD')),
    CONSTRAINT chk_attendance_status CHECK (status IN ('PRESENT', 'ABSENT', 'LATE', 'EXCUSED')),
    CONSTRAINT chk_attendance_scope CHECK (
        (attendance_type = 'DAILY' AND timetable_lesson_id IS NULL)
        OR (attendance_type = 'PERIOD' AND timetable_lesson_id IS NOT NULL)
    ),
    INDEX idx_attendance_school_stream_date (school_id, stream_id, attendance_date),
    INDEX idx_attendance_school_student_date (school_id, student_id, attendance_date),
    INDEX idx_attendance_school_term_date (school_id, term_id, attendance_date)
);
