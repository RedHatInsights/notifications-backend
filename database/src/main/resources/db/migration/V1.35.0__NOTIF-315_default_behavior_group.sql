ALTER TABLE behavior_group
    ALTER COLUMN account_id DROP NOT NULL;

ALTER TABLE endpoints
    ALTER COLUMN account_id DROP NOT NULL;

ALTER TABLE endpoint_email_subscriptions
    DROP CONSTRAINT fk_endpoint_email_subscriptions_application_id,
    ADD CONSTRAINT fk_endpoint_email_subscriptions_application_id FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE;
