-- Table: public.email_subscribers

CREATE TABLE public.endpoint_email_subscriptions
(
    account_id character varying(50) COLLATE pg_catalog."default" NOT NULL,
    user_id character varying(50) COLLATE pg_catalog."default" NOT NULL,
    subscription_type character varying(50) COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT application_event_type_pkey PRIMARY KEY (account_id, user_id, subscription_type)
)
