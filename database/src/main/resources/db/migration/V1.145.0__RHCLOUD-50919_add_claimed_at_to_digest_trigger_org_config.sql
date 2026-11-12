-- claimed_at records when a pod set a row to PROCESSING.
-- resetStaleClaims checks this column instead of next_run, so a row that was just
-- claimed after a long pause (next_run >> 6h ago) is not immediately reset by a
-- concurrent pod that also woke up.
ALTER TABLE digest_trigger_org_config
    ADD COLUMN claimed_at TIMESTAMP WITHOUT TIME ZONE;
