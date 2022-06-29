ALTER TABLE behavior_group
    ADD COLUMN org_id TEXT;
CREATE INDEX ix_behavior_group_org_id ON behavior_group (org_id);

ALTER TABLE endpoint_email_subscriptions
    -- Hibernate is unable to load an @EmbeddedId (EmailSubscriptionId) if one of its fields is null.
    -- As a consequence, the org_id DB column must always be non-null.
    -- TODO NOTIF-603 Remove the default value from the following column after the org ID migration is done.
    ADD COLUMN org_id TEXT DEFAULT 'PLACEHOLDER',
    DROP CONSTRAINT pk_endpoint_email_subscriptions,
    ADD CONSTRAINT pk_endpoint_email_subscriptions PRIMARY KEY (account_id, org_id, user_id, subscription_type, application_id);
CREATE INDEX ix_endpoint_email_subscriptions_org_id ON endpoint_email_subscriptions (org_id);

-- Hibernate is unable to load an @EmbeddedId (EmailSubscriptionId) if one of its fields is null.
-- As a consequence, the org_id DB column must always be non-null.
UPDATE endpoint_email_subscriptions
    SET org_id = 'PLACEHOLDER'
    WHERE org_id IS NULL;

ALTER TABLE endpoints
    ADD COLUMN org_id TEXT;
CREATE INDEX ix_endpoints_org_id ON endpoints (org_id);
