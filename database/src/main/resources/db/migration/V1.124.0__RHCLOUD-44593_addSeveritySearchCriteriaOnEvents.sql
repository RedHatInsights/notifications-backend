CREATE INDEX ix_event_org_id_severity_created ON event (org_id, severity, created DESC) INCLUDE (id);
