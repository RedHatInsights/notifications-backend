--
-- NOTIF-115, remove the join table as application -> eventType is 1:n
--

-- Add the new FK column
alter table public.event_type add column if not exists application_id uuid;

-- Migrate data over
update public.event_type
set application_id = aet.application_id
from public.application_event_type aet
WHERE
  aet.event_type_id = event_type.id
;

-- drop old join table
drop table public.application_event_type;

-- add a FK constraint on application_id column
alter table public.event_type add constraint et_app_fk
    foreign key (application_id) references applications(id)
    on delete cascade;

create unique index et_app_idx ON public.event_type( name, application_id)
