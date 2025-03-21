ALTER TABLE drawer_notification ADD COLUMN inventory_url text DEFAULT '' NOT NULL;
ALTER TABLE drawer_notification ADD COLUMN application_url text DEFAULT '' NOT NULL;

-- This overloads the existing prodecure insert_drawer_notifications_with_id, since the number of arguments has increased.
-- The old procedure is dropped afterwards
CREATE OR REPLACE PROCEDURE insert_drawer_notifications(userArray text, pOrgId text, pEventId uuid, pCreated timestamp, pInventoryUrl text, pApplicationUrl text)
    LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO drawer_notification(user_id, org_id, event_id, created, inventory_url, application_url)
    SELECT *, pOrgId, pEventId, pCreated, pInventoryUrl, pApplicationUrl FROM UNNEST(string_to_array(userArray,','))
    ON CONFLICT (org_id, user_id, event_id) DO NOTHING;
END;
$$;

DROP PROCEDURE insert_drawer_notifications(text, text, uuid, timestamp);
