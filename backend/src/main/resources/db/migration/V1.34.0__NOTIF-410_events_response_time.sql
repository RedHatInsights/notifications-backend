-- The response time improvement mostly comes from this.
CREATE INDEX ix_notification_history_event_id ON notification_history (event_id);

-- The following instructions change the Postgres autovacuum settings for several tables.

-- Indexes on tables with lots of DELETE or UPDATE operations tend to become less effective over time because Postgres
-- keeps the dead tuples that were deleted on the disk. Vacuuming the tables can fix that but the default autovacuum
-- settings are not suitable for tables with a large amount of records. By default, Postgres will autovacuum a table
-- when it contains [table total records] * autovacuum_vacuum_scale_factor (default: 0.2) + autovacuum_vacuum_threshold
-- (default: 50). This means that a table with 1 million records will be vacuumed when the dead tuples count reaches
-- 200050. Changing the autovacuum settings helps ensuring that autovacuum will run more often, will take less time and
-- won't be interrupted by transactions that would need to lock the vacuumed table.

-- The 'event' table contains ~3 million records on prod when this script is written. Old events are deleted at a rate
-- of ~4000 per hour. Assuming this rate remains constant, the autovacuum will happen approximately every hour instead
-- of every ~5 days with the default Postgres settings.
ALTER TABLE event SET (autovacuum_vacuum_scale_factor = 0);
ALTER TABLE event SET (autovacuum_vacuum_threshold = 5000);

-- The 'notification_history' table contains ~600 thousand records on prod when this script is written. Old events are
-- deleted at a rate of ~800 per hour. Assuming this rate remains constant, the autovacuum will happen approximately
-- every hour instead of every ~5 days with the default Postgres settings.
ALTER TABLE notification_history SET (autovacuum_vacuum_scale_factor = 0);
ALTER TABLE notification_history SET (autovacuum_vacuum_threshold = 1000);
