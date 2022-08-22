-- Table: behavior_group
DROP INDEX ix_behavior_group_account_id;

-- Table: email_aggregation
ALTER TABLE email_aggregation
    ALTER COLUMN account_id DROP NOT NULL,
    ALTER COLUMN org_id SET NOT NULL;

-- Table: endpoint_email_subscriptions
ALTER TABLE endpoint_email_subscriptions
    DROP CONSTRAINT pk_endpoint_email_subscriptions,
    DROP CONSTRAINT uq_endpoint_email_subscriptions,
    ALTER COLUMN account_id DROP NOT NULL,
    ALTER COLUMN org_id SET NOT NULL,
    ADD CONSTRAINT pk_endpoint_email_subscriptions PRIMARY KEY (org_id, user_id, subscription_type, application_id);

-- Table: endpoints
DROP INDEX ix_endpoints_account_id;

-- Table: event
DROP INDEX ix_event_account_id;
DROP INDEX ix_event_account_id_application_id;
DROP INDEX ix_event_index_only_scan;
ALTER TABLE event
    ALTER COLUMN account_id DROP NOT NULL,
    ALTER COLUMN org_id SET NOT NULL;
