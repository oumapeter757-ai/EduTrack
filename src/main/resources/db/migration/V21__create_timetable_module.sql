ALTER TABLE subjects
    ADD COLUMN required_room_type VARCHAR(32) NULL;

CREATE TABLE terms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    academic_year_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6),
    CONSTRAINT fk_term_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_term_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id),
    CONSTRAINT uk_term_school_year_name UNIQUE (school_id, academic_year_id, name),
    CONSTRAINT chk_term_dates CHECK (end_date >= start_date),
    INDEX idx_term_school (school_id),
    INDEX idx_term_academic_year (academic_year_id)
);

CREATE TABLE periods (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    sort_order INT NOT NULL,
    type VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6),
    CONSTRAINT fk_period_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT uk_period_school_sort UNIQUE (school_id, sort_order),
    CONSTRAINT chk_period_times CHECK (end_time > start_time),
    CONSTRAINT chk_period_type CHECK (type IN ('LESSON', 'BREAK')),
    INDEX idx_period_school (school_id)
);

CREATE TABLE rooms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    room_type VARCHAR(32) NOT NULL,
    capacity INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6),
    CONSTRAINT fk_room_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT uk_room_school_name UNIQUE (school_id, name),
    CONSTRAINT chk_room_capacity CHECK (capacity > 0),
    CONSTRAINT chk_room_type CHECK (room_type IN ('CLASSROOM', 'LAB', 'HALL', 'FIELD')),
    INDEX idx_room_school (school_id)
);

CREATE TABLE class_level_subjects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    class_level_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    default_lessons_per_week INT NOT NULL,
    compulsory BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6),
    CONSTRAINT fk_cls_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_cls_class_level FOREIGN KEY (class_level_id) REFERENCES class_levels(id),
    CONSTRAINT fk_cls_subject FOREIGN KEY (subject_id) REFERENCES subjects(id),
    CONSTRAINT uk_class_level_subject UNIQUE (class_level_id, subject_id),
    CONSTRAINT chk_cls_lessons CHECK (default_lessons_per_week > 0),
    INDEX idx_cls_school (school_id),
    INDEX idx_cls_subject (subject_id)
);

CREATE TABLE stream_subjects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    stream_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    academic_year_id BIGINT NOT NULL,
    teacher_id BIGINT NULL,
    lessons_per_week INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6),
    CONSTRAINT fk_ss_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_ss_stream FOREIGN KEY (stream_id) REFERENCES streams(id),
    CONSTRAINT fk_ss_subject FOREIGN KEY (subject_id) REFERENCES subjects(id),
    CONSTRAINT fk_ss_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id),
    CONSTRAINT fk_ss_teacher FOREIGN KEY (teacher_id) REFERENCES staff(id),
    CONSTRAINT uk_stream_subject_year UNIQUE (stream_id, subject_id, academic_year_id),
    CONSTRAINT chk_ss_lessons CHECK (lessons_per_week > 0),
    INDEX idx_ss_school (school_id),
    INDEX idx_ss_stream (stream_id),
    INDEX idx_ss_subject (subject_id),
    INDEX idx_ss_teacher (teacher_id)
);

CREATE TABLE timetables (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    term_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at DATETIME(6) NULL,
    published_by BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6),
    CONSTRAINT fk_timetable_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_timetable_term FOREIGN KEY (term_id) REFERENCES terms(id),
    CONSTRAINT fk_timetable_publisher FOREIGN KEY (published_by) REFERENCES users(id),
    CONSTRAINT uk_timetable_school_term UNIQUE (school_id, term_id),
    CONSTRAINT chk_timetable_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    INDEX idx_timetable_school (school_id)
);

CREATE TABLE timetable_lessons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    timetable_id BIGINT NOT NULL,
    stream_subject_id BIGINT NOT NULL,
    stream_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    room_id BIGINT NULL,
    period_id BIGINT NOT NULL,
    day_of_week SMALLINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6),
    CONSTRAINT fk_lesson_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_lesson_timetable FOREIGN KEY (timetable_id) REFERENCES timetables(id) ON DELETE CASCADE,
    CONSTRAINT fk_lesson_stream_subject FOREIGN KEY (stream_subject_id) REFERENCES stream_subjects(id),
    CONSTRAINT fk_lesson_stream FOREIGN KEY (stream_id) REFERENCES streams(id),
    CONSTRAINT fk_lesson_teacher FOREIGN KEY (teacher_id) REFERENCES staff(id),
    CONSTRAINT fk_lesson_room FOREIGN KEY (room_id) REFERENCES rooms(id),
    CONSTRAINT fk_lesson_period FOREIGN KEY (period_id) REFERENCES periods(id),
    CONSTRAINT uk_lesson_stream_slot UNIQUE (timetable_id, day_of_week, period_id, stream_id),
    CONSTRAINT uk_lesson_teacher_slot UNIQUE (timetable_id, day_of_week, period_id, teacher_id),
    CONSTRAINT uk_lesson_room_slot UNIQUE (timetable_id, day_of_week, period_id, room_id),
    CONSTRAINT chk_lesson_day CHECK (day_of_week BETWEEN 1 AND 7),
    INDEX idx_lesson_school (school_id),
    INDEX idx_lesson_timetable (timetable_id),
    INDEX idx_lesson_stream_subject (stream_subject_id),
    INDEX idx_lesson_teacher (teacher_id),
    INDEX idx_lesson_room (room_id),
    INDEX idx_lesson_period (period_id)
);

CREATE TABLE timetable_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id BIGINT NOT NULL,
    action VARCHAR(40) NOT NULL,
    actor_id BIGINT NULL,
    old_value TEXT NULL,
    new_value TEXT NULL,
    occurred_at DATETIME(6) NOT NULL,
    INDEX idx_timetable_audit_school_entity (school_id, entity_type, entity_id),
    INDEX idx_timetable_audit_actor (actor_id),
    CONSTRAINT fk_timetable_audit_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_timetable_audit_actor FOREIGN KEY (actor_id) REFERENCES users(id)
);
