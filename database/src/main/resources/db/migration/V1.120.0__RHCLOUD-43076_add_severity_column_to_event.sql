-- Create index for better query performance when filtering by severity
CREATE INDEX idx_event_severity ON event(severity);

