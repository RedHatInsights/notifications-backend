ALTER TABLE notification_history
    DROP COLUMN account_id,
    ALTER COLUMN event_id SET NOT NULL;

ALTER TABLE event
    ALTER COLUMN event_type_id SET NOT NULL;
