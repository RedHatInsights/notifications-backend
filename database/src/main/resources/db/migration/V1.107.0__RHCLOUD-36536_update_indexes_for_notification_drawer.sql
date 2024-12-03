DROP INDEX ix_drawer_notification_org_id_user_id_created_read_event_id;
CREATE INDEX ix_drawer_notification_org_id_user_id_read_created_event_id ON drawer_notification (org_id, user_id, read, created DESC, event_id);
