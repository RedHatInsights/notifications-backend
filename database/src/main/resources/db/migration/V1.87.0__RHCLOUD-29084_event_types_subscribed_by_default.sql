ALTER TABLE event_type
    ADD COLUMN subscribed_by_default BOOLEAN NOT NULL DEFAULT FALSE;
