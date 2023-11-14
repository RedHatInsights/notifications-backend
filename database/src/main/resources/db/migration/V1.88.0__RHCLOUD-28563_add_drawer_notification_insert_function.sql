CREATE OR REPLACE PROCEDURE insert_drawer_notifications_with_id(userArray text, pOrgId text, pEventId uuid, pCreated timestamp)
    LANGUAGE plpgsql
AS $$
DECLARE
    drawerNotification jsonb;
BEGIN
    FOREACH drawerNotification IN ARRAY (userArray::jsonb[])
        LOOP
            INSERT INTO drawer_notification(id, user_id, org_id, event_id, created)
            VALUES ((drawerNotification->>'drawerNotificationUuid')::uuid, drawerNotification->>'username', pOrgId, pEventId, pCreated)
            ON CONFLICT (org_id, user_id, event_id) DO NOTHING;
        END LOOP;
END;
$$;
