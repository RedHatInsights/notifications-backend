ALTER TABLE event
    ADD COLUMN bundle_id UUID,
    ADD COLUMN bundle_display_name VARCHAR,
    ADD COLUMN application_id UUID,
    ADD COLUMN application_display_name VARCHAR,
    ADD COLUMN event_type_display_name VARCHAR,
    ADD CONSTRAINT fk_event_bundle_id FOREIGN KEY (bundle_id) REFERENCES bundles (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_event_application_id FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE;

CREATE INDEX ix_event_index_only_scan ON event (account_id, bundle_id, application_id, event_type_display_name, created DESC, id);
