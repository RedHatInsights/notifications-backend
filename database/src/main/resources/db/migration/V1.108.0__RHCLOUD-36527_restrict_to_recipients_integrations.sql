ALTER TABLE event_type
    ADD COLUMN restrict_to_recipients_integrations BOOLEAN NOT NULL DEFAULT FALSE;
