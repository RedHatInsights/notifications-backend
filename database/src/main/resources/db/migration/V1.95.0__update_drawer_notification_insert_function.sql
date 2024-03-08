CREATE OR REPLACE PROCEDURE insert_drawer_notifications_with_id(userArray text, pOrgId text, pEventId uuid, pCreated timestamp)
    LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO drawer_notification(user_id, org_id, event_id, created)
    SELECT *, pOrgId, pEventId, pCreated FROM UNNEST(string_to_array(userArray,','))
    ON CONFLICT (org_id, user_id, event_id) DO NOTHING;
END;
$$;

DROP FUNCTION insert_drawer_notifications(text, text, uuid, timestamp);
ALTER PROCEDURE insert_drawer_notifications_with_id(text, text, uuid, timestamp) RENAME TO insert_drawer_notifications;

ALTER TABLE drawer_notification DROP COLUMN id;
