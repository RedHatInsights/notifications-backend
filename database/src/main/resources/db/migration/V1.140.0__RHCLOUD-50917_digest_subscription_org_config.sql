CREATE TABLE digest_subscription_org_config (
    org_id              TEXT NOT NULL,
    subscription_type   VARCHAR NOT NULL,
    cron_expression     VARCHAR(100) NOT NULL,
    last_run            TIMESTAMP,
    next_run            TIMESTAMP,
    PRIMARY KEY (org_id, subscription_type),
    CONSTRAINT chk_digest_sub_type CHECK (subscription_type IN ('DAILY', 'WEEKLY'))
);

CREATE INDEX ix_digest_sub_org_config_next_run
    ON digest_subscription_org_config (next_run);
