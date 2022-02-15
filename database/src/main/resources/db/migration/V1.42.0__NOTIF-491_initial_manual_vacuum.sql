-- This script should allow Postgres to run index-only scans on the following tables immediately, without waiting for
-- the next DB cleaning cron job execution.

VACUUM ANALYZE event;
VACUUM ANALYZE notification_history;
VACUUM ANALYZE kafka_message;
