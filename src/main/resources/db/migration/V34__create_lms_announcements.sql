-- V34: LMS Announcements
CREATE TABLE lms_announcement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT,
    posted_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lms_announcement_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_lms_announcement_course FOREIGN KEY (course_id) REFERENCES lms_course(id),
    INDEX idx_lms_announcement_course (course_id)
) ENGINE=InnoDB;
