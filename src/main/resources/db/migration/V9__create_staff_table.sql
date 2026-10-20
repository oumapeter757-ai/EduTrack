CREATE TABLE staff (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    user_id BIGINT,
    staff_number VARCHAR(50) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    middle_name VARCHAR(100),
    email VARCHAR(255),
    phone VARCHAR(50),
    gender VARCHAR(20),
    date_of_birth DATE,
    join_date DATE,
    department VARCHAR(100),
    designation VARCHAR(100),
    qualification VARCHAR(255),
    address_line_1 VARCHAR(255),
    address_line_2 VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(100),
    postal_code VARCHAR(20),
    profile_image_url VARCHAR(500),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6),

    CONSTRAINT uk_staff_school_staff_number UNIQUE (school_id, staff_number),
    CONSTRAINT fk_staff_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_staff_school ON staff(school_id);
CREATE INDEX idx_staff_school_department ON staff(school_id, department);
CREATE INDEX idx_staff_school_status ON staff(school_id, status);
CREATE INDEX idx_staff_user ON staff(user_id);
