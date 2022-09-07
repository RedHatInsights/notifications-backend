-- This only affects future inserted rows but not the existing ones.
ALTER TABLE endpoints
    ADD COLUMN server_errors INTEGER NOT NULL DEFAULT 0;

-- This updates existing rows.
UPDATE endpoints SET server_errors = 0;
