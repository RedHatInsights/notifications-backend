insert into  bundles (name, display_name, id, created)
    values ('platform','Platform', public.gen_random_uuid(), now() );

insert into applications (bundle_id, name, display_name, id, created)
    select b.id ,
            'notifications',
            'Internal',
            public.gen_random_uuid(),
             now()
             from bundles b
             where b.name = 'platform'
        ;

insert into event_type (application_id, name, display_name, id )
    select a.id ,
            'integration_failed',
            'Integration failed',
            public.gen_random_uuid()

    from applications a
    where a.name = 'notifications'
    ;

