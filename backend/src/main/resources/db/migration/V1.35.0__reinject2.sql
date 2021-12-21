insert into behavior_group (id, account_id, bundle_id, display_name, created )
    select public.gen_random_uuid(),
        null,
        b.id,
        'Integration_failed_mail',
        now()
    from bundles b
    where b.name = 'platform'
    ;

with etq AS (
    select et.id
    from event_type et
    where et.name = 'integration_failed'
)
insert into event_type_behavior (behavior_group_id, event_type_id, created )
    select bg.id, etq.id, now()
    from behavior_group bg, etq
    where bg.display_name = 'Integration_failed_mail'
    ;



