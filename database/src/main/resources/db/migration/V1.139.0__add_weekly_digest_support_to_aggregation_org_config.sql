-- Step 1: Add columns to support weekly digest preferences.
-- This migration is backward-compatible with the current application code:
--   - subscription_type defaults to 'DAILY', so existing INSERTs (which don't
--     provide it) continue to work.
--   - preferred_day is nullable, so existing daily rows are unaffected.
--   - The original PK on org_id is kept so that the current ON CONFLICT (org_id)
--     upserts still function. A composite unique constraint is added alongside
--     it for use by the next code release.

ALTER TABLE aggregation_org_config
    ADD COLUMN subscription_type VARCHAR NOT NULL DEFAULT 'DAILY';

ALTER TABLE aggregation_org_config
    ADD COLUMN preferred_day SMALLINT;

ALTER TABLE aggregation_org_config
    ADD CONSTRAINT uq_aggregation_org_config_org_type
        UNIQUE (org_id, subscription_type);

ALTER TABLE aggregation_org_config
    ADD CONSTRAINT chk_preferred_day_range
        CHECK (preferred_day BETWEEN 1 AND 7);

ALTER TABLE aggregation_org_config
    ADD CONSTRAINT chk_preferred_day_weekly
        CHECK (
            (subscription_type = 'WEEKLY' AND preferred_day IS NOT NULL)
            OR (subscription_type != 'WEEKLY' AND preferred_day IS NULL)
        );
