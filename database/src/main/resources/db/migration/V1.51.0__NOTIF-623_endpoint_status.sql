-- add a new status column

alter table endpoints add column status varchar(20);

update endpoints set status = 'READY' where status is null;

-- Endpoints that are either READY of FAILED will not be retrieved from the DB by the ReadyCheck scheduled job.
-- We don't need to index them.
create index status_idx ON endpoints (status) where status not in ('READY', 'FAILED');
