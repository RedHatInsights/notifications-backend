--
-- NOTIF-138
-- Hibernate Reactive does not support the jsonb type from PostgreSQL.
--

ALTER TABLE public.endpoint_webhooks
  ALTER COLUMN basic_authentication TYPE text;

ALTER TABLE public.email_aggregation
  ALTER COLUMN payload TYPE text;

ALTER TABLE public.notification_history
  ALTER COLUMN details TYPE text;
