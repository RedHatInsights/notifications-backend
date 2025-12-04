-- Add severity column to event table
ALTER TABLE event ADD COLUMN severity VARCHAR(20);

-- Create index for better query performance when filtering by severity
CREATE INDEX idx_event_severity ON event(severity);

