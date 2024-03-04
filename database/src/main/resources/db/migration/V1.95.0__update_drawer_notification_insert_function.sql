CREATE OR REPLACE PROCEDURE insert_drawer_notifications_with_id(userArray text, pOrgId text, pEventId uuid, pCreated timestamp)
    LANGUAGE plpgsql
AS $$
DECLARE
    userName text;
BEGIN
    FOREACH userName IN ARRAY (string_to_array(userArray,','))
        LOOP
            INSERT INTO drawer_notification(user_id, org_id, event_id, created)
            VALUES (userName, pOrgId, pEventId, pCreated)
            ON CONFLICT (org_id, user_id, event_id) DO NOTHING;
        END LOOP;
END;
$$;
DROP FUNCTION insert_drawer_notifications(text, text, uuid, timestamp);
ALTER PROCEDURE insert_drawer_notifications_with_id(text, text, uuid, timestamp) RENAME TO insert_drawer_notifications;

ALTER TABLE drawer_notification DROP COLUMN id;
