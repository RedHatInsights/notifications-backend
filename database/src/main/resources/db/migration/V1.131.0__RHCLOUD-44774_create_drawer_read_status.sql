CREATE TABLE IF NOT EXISTS drawer_read_status (
    org_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    event_id UUID NOT NULL,
    PRIMARY KEY (org_id, user_id, event_id),
    CONSTRAINT fk_drawer_read_status_event
        FOREIGN KEY (event_id)
        REFERENCES event(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_drawer_read_status_org_user ON drawer_read_status (org_id, user_id DESC);
CREATE INDEX IF NOT EXISTS ix_drawer_read_status_event_id ON drawer_read_status (event_id);
