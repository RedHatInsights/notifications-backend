-- This stored procedure deletes the event log entries that are no longer needed.
-- It is executed from an OpenShift CronJob.
CREATE PROCEDURE cleanEventLog() AS $$
DECLARE
    deleted INTEGER;
BEGIN
    RAISE INFO '% Event log purge starting. Entries older than 31 days will be deleted.', NOW();
    DELETE FROM event WHERE created < NOW() AT TIME ZONE 'UTC' - INTERVAL '31 days';
    GET DIAGNOSTICS deleted = ROW_COUNT;
    RAISE INFO '% Event log purge ended. % entries were deleted from the database.', NOW(), deleted;
END;
$$ LANGUAGE PLPGSQL;

-- This stored procedure deletes the Kafka messages IDs that are no longer needed.
-- It is executed from an OpenShift CronJob.
CREATE PROCEDURE cleanKafkaMessagesIds() AS $$
DECLARE
    deleted INTEGER;
BEGIN
    RAISE INFO '% Kafka messages purge starting. Entries older than 1 day will be deleted.', NOW();
    DELETE FROM kafka_message WHERE created < NOW() AT TIME ZONE 'UTC' - INTERVAL '1 days';
    GET DIAGNOSTICS deleted = ROW_COUNT;
    RAISE INFO '% Kafka messages purge ended. % entries were deleted from the database.', NOW(), deleted;
END;
$$ LANGUAGE PLPGSQL;
