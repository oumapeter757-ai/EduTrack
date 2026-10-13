-- Migration to create students table with multi-tenancy and audit fields

CREATE TABLE students (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    user_id BIGINT,
    admission_number VARCHAR(50) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    middle_name VARCHAR(100),
    date_of_birth DATE,
    gender VARCHAR(20),
    blood_group VARCHAR(20),
    email VARCHAR(255),
    phone VARCHAR(50),
    address_line_1 VARCHAR(255),
    address_line_2 VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(100),
    postal_code VARCHAR(20),
    profile_image_url VARCHAR(500),
    admission_date DATE,
    grade_level VARCHAR(50),
    section VARCHAR(50),
    roll_number VARCHAR(50),
    academic_year VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    emergency_contact_name VARCHAR(150),
    emergency_contact_phone VARCHAR(50),
    emergency_contact_relationship VARCHAR(50),
    guardian_name VARCHAR(150),
    guardian_email VARCHAR(255),
    guardian_phone VARCHAR(50),
    guardian_relationship VARCHAR(50),
    medical_notes TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_student_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE,
    CONSTRAINT fk_student_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uk_student_school_admission UNIQUE (school_id, admission_number)
);

CREATE INDEX idx_student_school_grade ON students(school_id, grade_level);
CREATE INDEX idx_student_school_status ON students(school_id, status);
CREATE INDEX idx_student_school_active ON students(school_id, active);
