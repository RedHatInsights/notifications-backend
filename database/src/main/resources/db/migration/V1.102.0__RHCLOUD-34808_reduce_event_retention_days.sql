-- This stored procedure deletes the event log entries that are no longer needed.
-- It is executed from an OpenShift CronJob.
CREATE OR REPLACE PROCEDURE cleanEventLog() AS $$
DECLARE
    deleted INTEGER;
BEGIN
    RAISE INFO '% Event log purge starting. Entries older than 15 days will be deleted.', NOW();
    DELETE FROM event WHERE created < NOW() AT TIME ZONE 'UTC' - INTERVAL '15 days';
    GET DIAGNOSTICS deleted = ROW_COUNT;
    RAISE INFO '% Event log purge ended. % entries were deleted from the database.', NOW(), deleted;
END;
$$ LANGUAGE PLPGSQL;
