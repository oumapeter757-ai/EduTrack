-- V32: LMS Content - Courses, Topics, Resources, Attachments
CREATE TABLE lms_course (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    stream_subject_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    created_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_lms_course_school_stream_subject UNIQUE (school_id, stream_subject_id),
    CONSTRAINT fk_lms_course_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_lms_course_stream_subject FOREIGN KEY (stream_subject_id) REFERENCES stream_subject(id),
    INDEX idx_lms_course_school (school_id)
) ENGINE=InnoDB;

CREATE TABLE lms_topic (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    position INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_lms_topic_course_position UNIQUE (course_id, position),
    CONSTRAINT fk_lms_topic_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_lms_topic_course FOREIGN KEY (course_id) REFERENCES lms_course(id)
) ENGINE=InnoDB;

CREATE TABLE lms_resource (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,
    type VARCHAR(16) NOT NULL CHECK (type IN ('FILE', 'LINK', 'TEXT')),
    title VARCHAR(255) NOT NULL,
    body TEXT,
    url VARCHAR(2048),
    published BOOLEAN NOT NULL DEFAULT FALSE,
    visible_from TIMESTAMP,
    created_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_lms_resource_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_lms_resource_topic FOREIGN KEY (topic_id) REFERENCES lms_topic(id),
    CONSTRAINT chk_lms_resource_link_url CHECK (type != 'LINK' OR url LIKE 'http%'),
    INDEX idx_lms_resource_topic (topic_id),
    INDEX idx_lms_resource_school (school_id)
) ENGINE=InnoDB;

CREATE TABLE lms_attachment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    owner_type VARCHAR(16) NOT NULL CHECK (owner_type IN ('RESOURCE', 'ASSIGNMENT', 'SUBMISSION')),
    owner_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_lms_attachment_owner_file UNIQUE (owner_type, owner_id, file_id),
    CONSTRAINT fk_lms_attachment_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_lms_attachment_file FOREIGN KEY (file_id) REFERENCES stored_file(id)
) ENGINE=InnoDB;
