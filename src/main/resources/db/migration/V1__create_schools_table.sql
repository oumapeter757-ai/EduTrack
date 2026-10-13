CREATE TABLE schools (
                         id BIGINT AUTO_INCREMENT PRIMARY KEY,
                         name VARCHAR(255) NOT NULL,
                         code VARCHAR(255) UNIQUE,
                         subdomain VARCHAR(255) UNIQUE,
                         address VARCHAR(255),
                         phone VARCHAR(255),
                         email VARCHAR(255),
                         subscription_plan VARCHAR(255),
                         active BOOLEAN NOT NULL DEFAULT TRUE,
                         created_at DATETIME(6) NOT NULL,
                         updated_at DATETIME(6)
);