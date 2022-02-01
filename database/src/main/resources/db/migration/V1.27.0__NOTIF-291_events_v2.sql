-- This replaces V1.23.0 to V1.26.0 which took too much time on stage and caused a deployment failure.

-- The notification_history table contains a huge number of records that are no longer needed because we have a 31 days
-- retention delay for that table. Normally, the EventLogCleaner scheduled job is supposed to delete the expired
-- records but its first execution won't work because there's way too much data: the SQL query will get stuck and put a
-- never-ending exclusive lock on the notification_history rows. To prevent this from happening, we need to clean the
-- expired records manually here, before the event log data migration is done below.

ALTER TABLE notification_history
    DROP CONSTRAINT IF EXISTS notification_history_event_id;

-- The table is empty on prod, no data will be lost.
DROP TABLE IF EXISTS event;

-- This will ease the first run of EventLogCleaner by reducing the amount of data it will have to delete.
-- The following date is based on the assumption that the event log retention delay is 31 days.
DELETE FROM notification_history
WHERE created < '2021-08-06T00:00:00.000000';

CREATE TABLE event (
   id UUID NOT NULL,
   account_id VARCHAR(50) NOT NULL,
   event_type_id UUID,
   payload TEXT,
   created TIMESTAMP NOT NULL,
   CONSTRAINT pk_event PRIMARY KEY (id),
   CONSTRAINT fk_event_event_type_id FOREIGN KEY (event_type_id) REFERENCES event_type (id) ON DELETE CASCADE
);

ALTER TABLE notification_history
    DROP COLUMN event_id,
    ADD COLUMN event_id UUID,
    ADD CONSTRAINT fk_notification_history_event_id FOREIGN KEY (event_id) REFERENCES event (id) ON DELETE CASCADE;

INSERT INTO event(id, account_id, created)
SELECT id, account_id, created
FROM notification_history
WHERE event_id IS NULL;

UPDATE notification_history
SET event_id = id
WHERE event_id IS NULL;

ALTER TABLE notification_history
    ALTER COLUMN event_id SET NOT NULL;
