INSERT INTO event(id, account_id, created)
SELECT id, account_id, created
FROM notification_history
WHERE event_id IS NULL;

UPDATE notification_history
SET event_id = id
WHERE event_id IS NULL;

ALTER TABLE notification_history
    ALTER COLUMN event_id SET NOT NULL;
