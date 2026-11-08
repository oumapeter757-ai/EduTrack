DELETE duplicate_rows
FROM callback_inbox duplicate_rows
JOIN callback_inbox retained_rows
  ON retained_rows.school_id = duplicate_rows.school_id
 AND retained_rows.channel = duplicate_rows.channel
 AND retained_rows.provider_id = duplicate_rows.provider_id
 AND retained_rows.id < duplicate_rows.id;

ALTER TABLE callback_inbox
    ADD CONSTRAINT uk_callback_inbox_school_channel_provider
    UNIQUE (school_id, channel, provider_id);
