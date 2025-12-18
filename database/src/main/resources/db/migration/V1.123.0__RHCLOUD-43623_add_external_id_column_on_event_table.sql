ALTER TABLE event ADD COLUMN external_id uuid;
CREATE INDEX ix_event_external_id ON event (external_id);
