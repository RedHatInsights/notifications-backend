-- Ensure next_run column has no NULL values before adding constraint
-- Any existing NULL values will be set to current timestamp
UPDATE digest_trigger_org_config
SET next_run = NOW()
WHERE next_run IS NULL;

-- Add NOT NULL constraint to next_run column
ALTER TABLE digest_trigger_org_config
    ALTER COLUMN next_run SET NOT NULL;

-- Add Status column
ALTER TABLE digest_trigger_org_config
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
