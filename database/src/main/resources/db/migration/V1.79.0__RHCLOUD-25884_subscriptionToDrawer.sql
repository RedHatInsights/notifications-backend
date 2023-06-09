ALTER TABLE email_subscriptions
    add column subscribed boolean NOT NULL default true;

ALTER TABLE email_subscriptions ALTER COLUMN subscribed DROP DEFAULT;
