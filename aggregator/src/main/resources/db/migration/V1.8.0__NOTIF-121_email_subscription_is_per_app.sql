--
-- Email subscription is per app.
--

-- Add the new column
alter table public.endpoint_email_subscriptions
  add column if not exists application character varying(255) NOT NULL default 'policies',
  add column if not exists bundle character varying(255) NOT NULL default 'insights';

-- Drop old PKEY
ALTER TABLE ONLY public.endpoint_email_subscriptions DROP CONSTRAINT endpoint_email_subscriptions_pkey;

-- Add new PKEY
ALTER TABLE ONLY public.endpoint_email_subscriptions
  ADD CONSTRAINT endpoint_email_subscriptions_pkey PRIMARY KEY (account_id, user_id, subscription_type, application, bundle);


-- Add cascade to missing FK
alter table endpoint_targets drop CONSTRAINT "FK_event_type_id_target";
alter table endpoint_targets add CONSTRAINT "FK_event_type_id_target"
  foreign key (event_type_id)
  references event_type(id)
  ON UPDATE CASCADE
  ON DELETE CASCADE;
