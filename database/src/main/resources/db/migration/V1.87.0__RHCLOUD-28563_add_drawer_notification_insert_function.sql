CREATE OR REPLACE FUNCTION insert_drawer_notifications_with_id(pUserIdList text, pOrgId text, pEventId uuid, pCreated timestamp)
    RETURNS SETOF drawer_notification AS
$BODY$
DECLARE
    userId text;
    userArray text[]=string_to_array(pUserIdList,',');
    splitedUserId text[];
BEGIN
    FOREACH userId IN ARRAY userArray
        LOOP
            splitedUserId=string_to_array(userId,'#');
            INSERT INTO drawer_notification(id, user_id, org_id, event_id, created)
            VALUES (splitedUserId[1]::uuid, splitedUserId[2], pOrgId, pEventId, pCreated)
            ON CONFLICT (org_id, user_id, event_id) DO NOTHING;
        END LOOP;
    RETURN QUERY SELECT * FROM drawer_notification where org_id=pOrgId and event_id=pEventId and userId = ANY (userArray);
END;
$BODY$
    LANGUAGE plpgsql;
