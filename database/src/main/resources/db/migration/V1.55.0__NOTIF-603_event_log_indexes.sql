DROP INDEX ix_event_org_id;

CREATE INDEX ix_event_org_id
    ON event (org_id, created DESC, id);

CREATE INDEX ix_event_org_id_application_id
    ON event (org_id, application_id, created DESC, id);

CREATE INDEX ix_event_org_id_bundle_id_application_id_event_type_display_name
    ON event (org_id, bundle_id, application_id, event_type_display_name, created DESC, id);
