-- V29: Create library catalogue tables
-- Library categories for organizing books
CREATE TABLE library_categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_category_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT uk_category_school_code UNIQUE (school_id, code),
    CONSTRAINT uk_category_school_name UNIQUE (school_id, name)
) ENGINE=InnoDB;

CREATE INDEX idx_category_school ON library_categories(school_id);

-- Books (titles)
CREATE TABLE books (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    title VARCHAR(500) NOT NULL,
    authors VARCHAR(1000),
    isbn VARCHAR(20),
    publisher VARCHAR(200),
    publication_year INTEGER,
    edition VARCHAR(50),
    language VARCHAR(50),
    lendable BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_book_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_book_category FOREIGN KEY (category_id) REFERENCES library_categories(id),
    CONSTRAINT chk_book_year CHECK (publication_year IS NULL OR publication_year > 0)
) ENGINE=InnoDB;

CREATE INDEX idx_book_school_category ON books(school_id, category_id);
CREATE INDEX idx_book_school_active ON books(school_id, active);

-- Book copies
CREATE TABLE book_copies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    book_number VARCHAR(50) NOT NULL,
    shelf_location VARCHAR(100),
    `condition` VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    acquired_on DATE,
    lost_charge_hint DECIMAL(10,2),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_copy_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_copy_book FOREIGN KEY (book_id) REFERENCES books(id),
    CONSTRAINT uk_copy_school_number UNIQUE (school_id, book_number),
    CONSTRAINT chk_copy_status CHECK (status IN ('AVAILABLE', 'ON_LOAN', 'LOST', 'DAMAGED', 'WITHDRAWN'))
) ENGINE=InnoDB;

CREATE INDEX idx_copy_school_book ON book_copies(school_id, book_id);
CREATE INDEX idx_copy_school_status ON book_copies(school_id, status);
