--
-- Create a new b/a/et platform/notifications/integration-failed
--   and then link that to a default behaviour group to send email
--   to admin
insert into  bundles (name, display_name, id, created)
    values ('console','Console', public.gen_random_uuid(), now() at time zone 'UTC' );

insert into applications (bundle_id, name, display_name, id, created)
    select b.id ,
            'notifications',
            'Internal',
            public.gen_random_uuid(),
            now() at time zone 'UTC'
    from bundles b
    where b.name = 'console'
        ;

insert into event_type (application_id, name, display_name, id )
    select a.id ,
            'integration-failed',
            'Integration failed',
            public.gen_random_uuid()
    from applications a
    where a.name = 'notifications'
    ;
