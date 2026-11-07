CREATE TABLE IF NOT EXISTS callback_inbox_duplicate_archive LIKE callback_inbox;

INSERT IGNORE INTO callback_inbox_duplicate_archive
SELECT duplicate_rows.*
FROM callback_inbox duplicate_rows
JOIN callback_inbox retained_rows
  ON retained_rows.school_id = duplicate_rows.school_id
 AND retained_rows.channel = duplicate_rows.channel
 AND retained_rows.provider_id = duplicate_rows.provider_id
 AND retained_rows.id < duplicate_rows.id;

DELETE duplicate_rows
FROM callback_inbox duplicate_rows
JOIN callback_inbox retained_rows
  ON retained_rows.school_id = duplicate_rows.school_id
 AND retained_rows.channel = duplicate_rows.channel
 AND retained_rows.provider_id = duplicate_rows.provider_id
 AND retained_rows.id < duplicate_rows.id;

SET @callback_unique_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'callback_inbox'
      AND index_name = 'uk_callback_inbox_school_channel_provider'
);

SET @callback_unique_sql := IF(
    @callback_unique_exists = 0,
    'ALTER TABLE callback_inbox ADD UNIQUE INDEX uk_callback_inbox_school_channel_provider (school_id, channel, provider_id)',
    'SELECT 1'
);

PREPARE callback_unique_statement FROM @callback_unique_sql;
EXECUTE callback_unique_statement;
DEALLOCATE PREPARE callback_unique_statement;
