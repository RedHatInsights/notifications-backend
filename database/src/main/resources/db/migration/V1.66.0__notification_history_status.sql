UPDATE notification_history SET status = CASE
    WHEN invocation_result IS true THEN 'SUCCESS'
    ELSE 'FAILED_INTERNAL'
    END WHERE status is NULL;
