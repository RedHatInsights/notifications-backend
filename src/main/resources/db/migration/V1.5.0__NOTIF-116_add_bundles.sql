create table bundles (id uuid primary key DEFAULT public.gen_random_uuid() NOT NULL,
    name character varying(255),
    display_name character varying,
    created timestamp with time zone DEFAULT now() NOT NULL,
    updated timestamp with time zone
);

alter table applications add column bundle_id uuid NOT NULL;

alter table applications add constraint app_bundle_fk
  foreign key (bundle_id) references bundles(id)
  on delete cascade;

create unique index app_bundle_idx ON public.applications(name, bundle_id);

