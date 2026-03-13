ALTER TABLE event_type DROP COLUMN include_in_drawer;
ALTER TABLE event_type ADD COLUMN included_in_drawer BOOLEAN NOT NULL DEFAULT FALSE;
