-- When the frontend integration page is displayed, the backend receives multiple REST queries to retrieve the last
-- connection attempt of each integration. The backend then selects all history entries matching the given
-- account/endpoint couple, then orders the records by descending order and finally returns a subset of the records
-- because the request is limited. The following index will make the ordering step much faster because Postgres will
-- no longer need to scan all notification_history records before sorting them.

CREATE INDEX ix_notification_history_endpoint_id_created
    ON notification_history (endpoint_id, created DESC);
