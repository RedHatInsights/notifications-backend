ALTER TABLE email_subscriptions
    ADD COLUMN severities jsonb;

ALTER TABLE event
    ADD COLUMN severity VARCHAR(20);
