-- Drop stored procedure
DROP PROCEDURE IF EXISTS insert_drawer_notifications(text, text, uuid, timestamp);

-- Drop indexes (will be removed when table is dropped, but explicit for clarity)
DROP INDEX IF EXISTS ix_drawer_notification_org_id_user_id_read_created_event_id;
DROP INDEX IF EXISTS ix_drawer_notification_event_id;

-- Drop table (CASCADE will drop foreign key constraints)
DROP TABLE IF EXISTS drawer_notification CASCADE;
