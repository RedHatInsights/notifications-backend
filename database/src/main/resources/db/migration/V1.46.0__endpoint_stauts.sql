-- add a new status column

alter table endpoints add column status integer ;

update endpoints set status = 0 where status is null;

create index status_idx ON endpoints (status);
