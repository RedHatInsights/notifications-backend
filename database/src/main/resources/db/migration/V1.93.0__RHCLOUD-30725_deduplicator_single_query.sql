ALTER TABLE kafka_message
    ALTER COLUMN created SET DEFAULT (NOW() AT TIME ZONE 'utc');
