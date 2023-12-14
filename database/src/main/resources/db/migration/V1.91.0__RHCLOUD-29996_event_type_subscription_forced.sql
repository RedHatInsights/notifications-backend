ALTER TABLE event_type
    ADD COLUMN subscription_locked BOOLEAN NOT NULL DEFAULT FALSE;
