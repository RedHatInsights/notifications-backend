create table bundles (id uuid primary key DEFAULT public.gen_random_uuid() NOT NULL,
    name character varying(255),
    display_name character varying,
    created timestamp with time zone DEFAULT now() NOT NULL,
    updated timestamp with time zone
);

insert into bundles (name, display_name) VALUES ('insights', 'Insights');

-- we can not set this to NOT NULL here, as we first need to link the bundle
-- to the existing 'Policies' app (see v1.1.1)
ALTER TABLE applications add column bundle_id uuid;

update applications set bundle_id = (
select id from bundles where name = 'insights'
);


alter table applications add constraint app_bundle_fk
  foreign key (bundle_id) references bundles(id)
  on delete cascade;



create unique index app_bundle_idx ON public.applications(name, bundle_id);

