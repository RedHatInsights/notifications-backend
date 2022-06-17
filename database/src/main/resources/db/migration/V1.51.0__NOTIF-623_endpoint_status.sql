-- add a new status column

alter table endpoints add column status integer;

update endpoints set status = 0 where status is null;

-- Endpoints that are either READY of FAILED will not be retrieved from the DB by the ReadyCheck scheduled job.
-- We don't need to index them.
create index status_idx ON endpoints (status) where status not in (0, 5);
