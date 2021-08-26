-- The notification_history table contains a huge number of records that are no longer needed because we have a 31 days
-- retention delay for that table. Normally, the EventLogCleaner scheduled job is supposed to delete the expired
-- records but its first execution won't work because there's way too much data: the SQL query will get stuck and put a
-- never-ending exclusive lock on the notification_history rows. To prevent this from happening, we need to clean the
-- expired records manually here, before the event log data migration is done below.
DELETE FROM notification_history
WHERE created < '2021-07-26T00:00:00.000000';

INSERT INTO event(id, account_id, created)
SELECT id, account_id, created
FROM notification_history
WHERE event_id IS NULL;

UPDATE notification_history
SET event_id = id
WHERE event_id IS NULL;

ALTER TABLE notification_history
    ALTER COLUMN event_id SET NOT NULL;
