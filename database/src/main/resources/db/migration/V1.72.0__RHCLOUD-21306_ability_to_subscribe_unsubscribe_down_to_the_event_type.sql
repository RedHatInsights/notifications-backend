CREATE TABLE public.email_subscriptions (
    account_id varchar(50) NULL,
    user_id varchar(50) NOT NULL,
    org_id text NOT NULL,
    event_type_id uuid NOT NULL,
    subscription_type varchar(50) NOT NULL,
    CONSTRAINT pk_email_subscriptions PRIMARY KEY (org_id, user_id, subscription_type, event_type_id)
);
CREATE INDEX ix_email_subscriptions_org_id ON public.email_subscriptions USING btree (org_id);

-- public.endpoint_email_subscriptions foreign keys
ALTER TABLE public.email_subscriptions ADD CONSTRAINT fk_email_subscriptions_event_type FOREIGN KEY (event_type_id) REFERENCES public.event_type(id) ON DELETE CASCADE;
