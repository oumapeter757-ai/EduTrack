-- V16: name the unique constraints on schools.code and schools.subdomain so that
-- the application can map DataIntegrityViolationException to a specific field.
--
-- Pre-check (run manually before applying in environments with existing data):
--   SELECT code, COUNT(*) c FROM schools GROUP BY code HAVING c > 1;
--   SELECT subdomain, COUNT(*) c FROM schools GROUP BY subdomain HAVING c > 1;
-- Clean duplicates first; this migration will fail otherwise.
--
-- MySQL 8: ALTER TABLE will fail if a differently-named unique key already covers
-- the same column, so we try to drop the auto-generated keys first (ignore errors
-- if they do not exist in your environment).

-- Drop any pre-existing anonymous unique keys on these columns (safe no-ops if missing).
-- Developers on fresh schemas can skip these DROPs.
-- ALTER TABLE schools DROP INDEX code;
-- ALTER TABLE schools DROP INDEX subdomain;

ALTER TABLE schools
    ADD CONSTRAINT uk_school_code UNIQUE (code);

ALTER TABLE schools
    ADD CONSTRAINT uk_school_subdomain UNIQUE (subdomain);
