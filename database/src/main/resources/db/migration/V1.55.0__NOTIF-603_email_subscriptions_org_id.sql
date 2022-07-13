-- Migration script V1.52.0 added the 'PLACEHOLDER' value to the 'org_id' field
-- of all 'endpoint_email_subscriptions' rows. Since that migration, some users
-- changed their subscription preferences and the 'endpoint_email_subscriptions'
-- may now contain two rows for the same account_id/user_id/subscription_type/application_id:
-- one with 'PLACEHOLDER', the other one with their real 'org_id'. This query
-- will delete all duplicates (and only duplicates) that contain 'PLACEHOLDER'.
DELETE FROM endpoint_email_subscriptions es1
WHERE org_id = 'PLACEHOLDER'
    AND EXISTS (
        SELECT 1 FROM endpoint_email_subscriptions es2
        WHERE es1.account_id = es2.account_id
            AND es1.user_id = es2.user_id
            AND es1.subscription_type = es2.subscription_type
            AND es1.application_id = es2.application_id
            AND es2.org_id <> 'PLACEHOLDER'
    );

ALTER TABLE endpoint_email_subscriptions
    DROP CONSTRAINT pk_endpoint_email_subscriptions,
    ADD CONSTRAINT pk_endpoint_email_subscriptions PRIMARY KEY (account_id, user_id, subscription_type, application_id),
    ADD CONSTRAINT uq_endpoint_email_subscriptions UNIQUE (account_id, org_id, user_id, subscription_type, application_id);

UPDATE endpoint_email_subscriptions
SET org_id = NULL
WHERE org_id = 'PLACEHOLDER';
