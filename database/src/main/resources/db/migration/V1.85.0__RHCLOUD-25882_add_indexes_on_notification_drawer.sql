CREATE INDEX ix_drawer_notification_org_id_user_id_created_read_event_id ON drawer_notification (org_id, user_id, created DESC, read, event_id);
CREATE INDEX ix_drawer_notification_event_id ON drawer_notification (event_id); -- add index on event id for event delete cascading performances
