-- Backfill FREE subscriptions first. FREE never carries subscription dates.
UPDATE schools
SET subscription_start_date = NULL,
    subscription_end_date = NULL
WHERE subscription_tier = 'FREE';

-- Backfill missing start dates for paid tiers from the existing creation date.
UPDATE schools
SET subscription_start_date = DATE(created_at)
WHERE subscription_tier <> 'FREE'
  AND subscription_start_date IS NULL;

-- Backfill missing end dates for paid tiers using 30 days after the start date.
UPDATE schools
SET subscription_end_date = DATE_ADD(subscription_start_date, INTERVAL 30 DAY)
WHERE subscription_tier <> 'FREE'
  AND subscription_end_date IS NULL;

ALTER TABLE schools
    ALTER COLUMN subscription_tier SET DEFAULT 'FREE';

ALTER TABLE schools
    ADD CONSTRAINT chk_schools_subscription_terms
    CHECK (
        (
            subscription_tier = 'FREE'
            AND subscription_start_date IS NULL
            AND subscription_end_date IS NULL
        )
        OR
        (
            subscription_tier <> 'FREE'
            AND subscription_start_date IS NOT NULL
            AND subscription_end_date IS NOT NULL
            AND subscription_end_date > subscription_start_date
        )
    );
