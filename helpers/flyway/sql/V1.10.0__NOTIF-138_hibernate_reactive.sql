--
-- Hibernate Reactive 1.0.0.CR1 does not support:
--   - the TIMESTAMP WITH TIME ZONE column type
--   - database-generated values (uuid or dates) retrieval during an insert or update
--

ALTER TABLE public.applications
  ALTER COLUMN id DROP DEFAULT,
  ALTER COLUMN display_name SET NOT NULL,
  ALTER COLUMN created TYPE TIMESTAMP,
  ALTER COLUMN created DROP DEFAULT,
  ALTER COLUMN updated TYPE TIMESTAMP;

ALTER TABLE public.bundles
  ALTER COLUMN id DROP DEFAULT,
  ALTER COLUMN name SET NOT NULL,
  ALTER COLUMN display_name SET NOT NULL,
  ALTER COLUMN created TYPE TIMESTAMP,
  ALTER COLUMN created DROP DEFAULT,
  ALTER COLUMN updated TYPE TIMESTAMP;

ALTER TABLE public.email_aggregation
  ALTER COLUMN created TYPE TIMESTAMP,
  ALTER COLUMN created DROP DEFAULT;

ALTER TABLE public.endpoints
  ALTER COLUMN id DROP DEFAULT,
  ALTER COLUMN created TYPE TIMESTAMP,
  ALTER COLUMN created DROP DEFAULT,
  ALTER COLUMN updated TYPE TIMESTAMP;

ALTER TABLE public.event_type
  ALTER COLUMN id DROP DEFAULT;

ALTER TABLE public.notification_history
  ALTER COLUMN created TYPE TIMESTAMP,
  ALTER COLUMN created DROP DEFAULT;
