
ALTER TABLE event ADD COLUMN has_authorization_criterion BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX ix_event_org_id_created_authorization_criterion ON event (org_id, created, has_authorization_criterion) include (id);
DROP INDEX ix_event_org_id;
