CREATE TABLE event_deduplication (
    event_type_id UUID NOT NULL,
    deduplication_key TEXT NOT NULL,
    delete_after TIMESTAMP NOT NULL,
    CONSTRAINT pk_event_deduplication PRIMARY KEY (event_type_id, deduplication_key),
    CONSTRAINT fk_event_deduplication_event_type_id FOREIGN KEY (event_type_id) REFERENCES event_type(id) ON DELETE CASCADE
);

CREATE INDEX ix_event_deduplication_delete_after ON event_deduplication(delete_after);
COMMENT ON INDEX ix_event_deduplication_delete_after IS 'Improves performance of the periodic cleanup query in cleanEventDeduplication procedure';

-- This stored procedure deletes the event deduplication entries that are no longer needed.
-- It is executed from an OpenShift CronJob.
CREATE PROCEDURE cleanEventDeduplication() AS $$
DECLARE
    deleted INTEGER;
BEGIN
    RAISE INFO '% Event deduplication purge starting. All entries with a delete_after timestamp in the past will be deleted.', NOW();
    DELETE FROM event_deduplication WHERE NOW() AT TIME ZONE 'UTC' > delete_after;
    GET DIAGNOSTICS deleted = ROW_COUNT;
    RAISE INFO '% Event deduplication purge ended. % entries were deleted from the database.', NOW(), deleted;
END;
$$ LANGUAGE PLPGSQL;
