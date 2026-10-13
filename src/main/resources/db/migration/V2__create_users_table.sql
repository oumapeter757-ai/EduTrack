CREATE TABLE users (
                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                       school_id BIGINT NOT NULL,
                       username VARCHAR(255) NOT NULL UNIQUE,
                       password VARCHAR(255) NOT NULL,
                       first_name VARCHAR(255) NOT NULL,
                       last_name VARCHAR(255) NOT NULL,
                       email VARCHAR(255) NOT NULL UNIQUE,
                       active BOOLEAN NOT NULL DEFAULT TRUE,
                       role VARCHAR(50) NOT NULL,

    -- Assuming your BaseEntity includes these standard audit fields:
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                       CONSTRAINT fk_user_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE
);