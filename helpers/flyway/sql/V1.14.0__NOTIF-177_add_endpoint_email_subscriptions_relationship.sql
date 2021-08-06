-- WARNING!
-- This script will delete any endpoint_email_subscriptions record that would not contain a valid bundle/application couple.

ALTER TABLE endpoint_email_subscriptions
    ADD COLUMN application_id uuid;

UPDATE endpoint_email_subscriptions s
    SET application_id = (SELECT a.id FROM applications a, bundles b WHERE a.name = s.application AND b.name = s.bundle AND a.bundle_id = b.id);

DELETE FROM endpoint_email_subscriptions
    WHERE application_id IS NULL;

ALTER TABLE endpoint_email_subscriptions
    DROP CONSTRAINT endpoint_email_subscriptions_pkey,
    DROP COLUMN application,
    DROP COLUMN bundle,
    ALTER COLUMN application_id SET NOT NULL,
    ADD CONSTRAINT fk_endpoint_email_subscriptions_application_id FOREIGN KEY (application_id) REFERENCES applications(id),
    ADD CONSTRAINT pk_endpoint_email_subscriptions PRIMARY KEY (account_id, user_id, subscription_type, application_id);
