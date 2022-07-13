ALTER TABLE endpoint_email_subscriptions
    ALTER COLUMN org_id DROP NOT NULL,
    ALTER COLUMN org_id DROP DEFAULT;
