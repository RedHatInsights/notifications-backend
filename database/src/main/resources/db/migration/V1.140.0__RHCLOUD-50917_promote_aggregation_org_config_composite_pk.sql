-- Step 2/3: Promote composite unique constraint to primary key.
-- Safe to apply before the new code deploys: existing code only creates
-- DAILY rows, so the single-column PK and the composite PK are equivalent
-- for the current data set. The old single-column PK is dropped first.

ALTER TABLE aggregation_org_config
    DROP CONSTRAINT aggregation_org_config_pkey;

ALTER TABLE aggregation_org_config
    DROP CONSTRAINT uq_aggregation_org_config_org_type;

ALTER TABLE aggregation_org_config
    ADD PRIMARY KEY (org_id, subscription_type);
