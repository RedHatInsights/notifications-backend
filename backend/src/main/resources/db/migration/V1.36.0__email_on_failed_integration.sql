--
-- Create a new b/a/et platform/notifications/integration_failed
--   and then link that to a default behaviour group to send email
--   to admin
insert into  bundles (name, display_name, id, created)
    values ('platform','Platform', public.gen_random_uuid(), now() at time zone 'UTC' );

insert into applications (bundle_id, name, display_name, id, created)
    select b.id ,
            'notifications',
            'Internal',
            public.gen_random_uuid(),
            now() at time zone 'UTC'
    from bundles b
    where b.name = 'platform'
        ;

insert into event_type (application_id, name, display_name, id )
    select a.id ,
            'integration-failed',
            'Integration failed',
            public.gen_random_uuid()
    from applications a
    where a.name = 'notifications'
    ;

-- create the default BG with accountId = null
insert into behavior_group (id, account_id, bundle_id, display_name, created )
    select public.gen_random_uuid(),
        null,
        b.id,
        'Integration_failed_mail',
        now() at time zone 'UTC'
    from bundles b
    where b.name = 'platform'
    ;

-- Link this to the event type
with etq AS (
    select et.id
    from event_type et
    where et.name = 'integration_failed'
)
insert into event_type_behavior (behavior_group_id, event_type_id, created )
    select bg.id,
        etq.id,
        now() at time zone 'UTC'
    from behavior_group bg, etq
    where bg.display_name = 'Integration_failed_mail'
    ;

-- create a default email endpoint (again accountId == null means default)
insert into endpoints (id, account_id, created, description, enabled, endpoint_type, name)
    values(
        public.gen_random_uuid(),
        null, -- no account id for default BG
        now() at time zone 'UTC',
        'Default integration failed email ep',
        true,
        1, -- email
        'failed_integration'
        )
    ;
-- add endpoint properties
with epq AS (
    select ep.id
    from endpoints ep
    where ep.name = 'failed_integration' and ep.account_id is NULL
)
insert into email_properties(id, ignore_preferences, only_admins)
     select epq.id,
        true,
        true
     from epq
     ;

-- And link BG and the default email EP.
with epq AS (
    select ep.id
    from endpoints ep
    where ep.name = 'failed_integration' and ep.account_id is NULL
)
insert into behavior_group_action (behavior_group_id, endpoint_id, created, position)
    select bg.id,
        epq.id,
        now() at time zone 'UTC',
        0
    from behavior_group bg, epq
    where bg.display_name = 'Integration_failed_mail'
    ;

