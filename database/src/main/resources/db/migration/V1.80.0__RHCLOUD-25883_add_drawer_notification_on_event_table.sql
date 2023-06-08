ALTER TABLE event add column rendered_drawer_notification TEXT;

CREATE OR REPLACE FUNCTION insert_drawer_notifications(pUserIdList text, pOrgId text, pEventId uuid, pCreated timestamp)
    RETURNS SETOF drawer_notification AS
$BODY$
DECLARE
    createdNotifications REFCURSOR;
    userId text;
    userArray text[]=string_to_array(pUserIdList,',');
BEGIN
    FOREACH userId IN ARRAY userArray
        LOOP
            INSERT INTO drawer_notification(id, user_id, org_id, event_id, created)
            VALUES (gen_random_uuid(), userId, pOrgId, pEventId, pCreated)
            ON CONFLICT (org_id, user_id, event_id) DO NOTHING;
        END LOOP;
    RETURN QUERY SELECT * FROM drawer_notification where org_id=pOrgId and event_id=pEventId and userId = ANY (userArray);
END;
$BODY$
    LANGUAGE plpgsql;
