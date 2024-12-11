delete from endpoints where endpoint_type_v2 = 'DRAWER' and org_id is not null and not exists (select 1 from endpoint_event_type where endpoint_id = id);
