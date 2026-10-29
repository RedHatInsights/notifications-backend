CREATE TABLE IF NOT EXISTS subscription_change_log (
    id              uuid NOT NULL PRIMARY KEY,
    org_id          text NOT NULL,
    user_id         varchar(50) NOT NULL,
    event_type_id   uuid NOT NULL REFERENCES event_type(id) ON DELETE CASCADE,
    subscription_type varchar(50) NOT NULL,
    subscribed      boolean NOT NULL,
    changed_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_scl_event_type_changed_at ON subscription_change_log (event_type_id, changed_at);
CREATE INDEX IF NOT EXISTS ix_scl_org_user              ON subscription_change_log (org_id, user_id);
