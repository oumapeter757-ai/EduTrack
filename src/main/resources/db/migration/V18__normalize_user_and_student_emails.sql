-- Pre-check normalized email collisions before changing existing values.
CREATE TABLE IF NOT EXISTS migration_email_normalization_conflicts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_name VARCHAR(64) NOT NULL,
    normalized_email VARCHAR(255) NOT NULL,
    row_ids VARCHAR(2000) NOT NULL,
    detected_at DATETIME(6) NOT NULL
);

INSERT INTO migration_email_normalization_conflicts
    (table_name, normalized_email, row_ids, detected_at)
SELECT 'users', LOWER(TRIM(email)), GROUP_CONCAT(id ORDER BY id), CURRENT_TIMESTAMP(6)
FROM users
WHERE email IS NOT NULL
GROUP BY LOWER(TRIM(email))
HAVING COUNT(*) > 1;

INSERT INTO migration_email_normalization_conflicts
    (table_name, normalized_email, row_ids, detected_at)
SELECT 'students', LOWER(TRIM(email)), GROUP_CONCAT(id ORDER BY id), CURRENT_TIMESTAMP(6)
FROM students
WHERE email IS NOT NULL AND TRIM(email) <> ''
GROUP BY school_id, LOWER(TRIM(email))
HAVING COUNT(*) > 1;

INSERT INTO migration_email_normalization_conflicts
    (table_name, normalized_email, row_ids, detected_at)
SELECT 'users', LOWER(TRIM(email)), GROUP_CONCAT(id ORDER BY id), CURRENT_TIMESTAMP(6)
FROM users
WHERE email IS NOT NULL AND email <> LOWER(TRIM(email))
GROUP BY LOWER(TRIM(email));

INSERT INTO migration_email_normalization_conflicts
    (table_name, normalized_email, row_ids, detected_at)
SELECT 'students', LOWER(TRIM(email)), GROUP_CONCAT(id ORDER BY id), CURRENT_TIMESTAMP(6)
FROM students
WHERE email IS NOT NULL AND TRIM(email) <> '' AND email <> LOWER(TRIM(email))
GROUP BY school_id, LOWER(TRIM(email));

UPDATE users SET email = LOWER(TRIM(email)) WHERE email IS NOT NULL;
UPDATE students
SET email = NULLIF(LOWER(TRIM(email)), '')
WHERE email IS NOT NULL;
