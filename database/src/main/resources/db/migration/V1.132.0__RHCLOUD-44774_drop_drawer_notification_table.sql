DROP PROCEDURE IF EXISTS insert_drawer_notifications(text, text, uuid, timestamp);

DROP INDEX IF EXISTS ix_drawer_notification_org_id_user_id_read_created_event_id;
DROP INDEX IF EXISTS ix_drawer_notification_event_id;

DROP TABLE IF EXISTS drawer_notification CASCADE;
