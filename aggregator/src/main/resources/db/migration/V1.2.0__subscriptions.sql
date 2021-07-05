-- Table: public.email_subscribers

ALTER TABLE public.endpoint_email_subscriptions RENAME COLUMN event_type TO subscription_type;
