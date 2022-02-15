-- We no longer need Postgres to run the VACUUM operation automatically on the 'event' and 'notification_history' tables
-- because we are now running a manual VACUUM ANALYZE during the DB cleaning cron job execution.

ALTER TABLE event RESET (autovacuum_vacuum_scale_factor, autovacuum_vacuum_threshold);
ALTER TABLE notification_history RESET (autovacuum_vacuum_scale_factor, autovacuum_vacuum_threshold);
