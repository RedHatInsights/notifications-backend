ALTER TABLE notification_history
    ADD COLUMN status varchar(20);

UPDATE notification_history SET status = CASE
    WHEN invocation_result = true THEN 'SUCCESS'
    ELSE 'FAILED_PROCESSING'
    END;
