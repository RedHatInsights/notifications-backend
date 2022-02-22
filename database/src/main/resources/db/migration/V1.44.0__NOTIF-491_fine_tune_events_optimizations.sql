ALTER TABLE event
    ALTER COLUMN bundle_id SET NOT NULL,
    ALTER COLUMN bundle_display_name SET NOT NULL,
    ALTER COLUMN application_id SET NOT NULL,
    ALTER COLUMN application_display_name SET NOT NULL,
    ALTER COLUMN event_type_display_name SET NOT NULL;

DROP INDEX ix_event_account_id;
DROP INDEX ix_event_service_index_only_scan_asc;
DROP INDEX ix_event_service_index_only_scan_desc;

-- This index will speed up queries filtered by account_id only.
CREATE INDEX ix_event_account_id ON event (account_id, created DESC, id);

-- This index will speed up the Drift dashboard queries.
CREATE INDEX ix_event_account_id_application_id ON event (account_id, application_id, created DESC, id);

-- ix_event_index_only_scan (see V1.43.0) is still active after this migration.
