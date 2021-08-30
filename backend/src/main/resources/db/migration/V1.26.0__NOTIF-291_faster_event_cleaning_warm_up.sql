-- This replaces V1.25.0 which took too much time on stage

-- This will ease the first run of EventLogCleaner by reducing the amount of data it will have to delete.
-- The following date is based on the assumption that the event log retention delay is 31 days.

DELETE FROM notification_history
WHERE created < '2021-08-02T00:00:00.000000';

DELETE FROM event
WHERE created < '2021-08-02T00:00:00.000000';
