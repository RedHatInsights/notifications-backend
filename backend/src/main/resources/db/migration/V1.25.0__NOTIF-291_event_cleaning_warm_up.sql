-- This will ease the first run of EventLogCleaner by reducing the amount of data it will have to delete.
-- The following date is based on the assumption that the event log retention delay is 31 days.

DELETE FROM event
WHERE created < '2021-08-02T00:00:00.000000';
