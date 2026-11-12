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

-- claimed_at records when a pod set a row to PROCESSING.
-- resetStaleClaims checks this column instead of next_run, so a row that was just
-- claimed after a long pause (next_run >> 6h ago) is not immediately reset by a
-- concurrent pod that also woke up.
ALTER TABLE digest_trigger_org_config
    ADD COLUMN claimed_at TIMESTAMP WITHOUT TIME ZONE;