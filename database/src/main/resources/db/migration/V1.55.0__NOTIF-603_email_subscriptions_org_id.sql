ALTER TABLE endpoint_email_subscriptions
    DROP CONSTRAINT pk_endpoint_email_subscriptions,
    ADD CONSTRAINT pk_endpoint_email_subscriptions PRIMARY KEY (account_id, user_id, subscription_type, application_id),
    ADD CONSTRAINT uq_endpoint_email_subscriptions UNIQUE (account_id, org_id, user_id, subscription_type, application_id);

UPDATE endpoint_email_subscriptions
SET org_id = NULL
WHERE org_id = 'PLACEHOLDER';
