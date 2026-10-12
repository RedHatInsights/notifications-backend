ALTER TABLE digest_subscription_org_config RENAME TO digest_trigger_org_config;

ALTER INDEX ix_digest_sub_org_config_next_run RENAME TO ix_digest_trigger_org_config_next_run;

ALTER TABLE digest_trigger_org_config
    RENAME CONSTRAINT chk_digest_sub_type TO chk_digest_trigger_type;
