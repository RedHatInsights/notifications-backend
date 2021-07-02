ALTER TABLE event_type ADD COLUMN description CHARACTER VARYING;

-- ALTER TABLE event_type DROP INDEX "PK_event_type_id";
ALTER TABLE event_type RENAME COLUMN id TO oldId;



alter table endpoint_targets drop CONSTRAINT "FK_event_type_id_target" ;
alter table endpoint_targets rename column event_type_id TO eti; -- out of way
alter table endpoint_targets add column event_type_id UUID;

ALTER TABLE event_type ADD COLUMN id UUID UNIQUE;
ALTER TABLE event_type ALTER COLUMN id SET DEFAULT public.gen_random_uuid();

update event_type set id =  DEFAULT where id is null;

-- transition endpoint_targets.event_type_id
with X as ( select id from event_type et, endpoint_targets ept where  et.oldId = ept.eti )
    update endpoint_targets set event_type_id = id FROM x;

alter table endpoint_targets drop column eti;
alter table event_type drop column oldId;


alter table endpoint_targets add CONSTRAINT "FK_event_type_id_target"
    foreign key (event_type_id)
    references event_type(id);

ALTER TABLE event_type ADD PRIMARY KEY (id);

Drop SEQUENCE event_type_id_seq;
