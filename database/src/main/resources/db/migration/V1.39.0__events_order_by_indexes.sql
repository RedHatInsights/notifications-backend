-- The following indexes will make the ordering of events on their creation date much faster because Postgres will no
-- longer need to scan all event records before sorting them.

CREATE INDEX ix_event_account_id_created_asc
    ON event (account_id, created);

CREATE INDEX ix_event_account_id_created_desc
    ON event (account_id, created DESC);
