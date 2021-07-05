--
-- This script changes the notification_history.id type from integer to uuid.
-- We need that for the delivery-via-camel PoC, but it will also be better if we decide to expose the history through an
-- API in the future (exposing an auto-incremented integer id would be a security flaw).
--

ALTER TABLE notification_history DROP CONSTRAINT notification_history_pkey;
ALTER TABLE notification_history DROP COLUMN id;
ALTER TABLE notification_history ADD COLUMN id uuid DEFAULT public.gen_random_uuid() NOT NULL;
ALTER TABLE notification_history ALTER COLUMN id DROP DEFAULT;
ALTER TABLE notification_history ADD CONSTRAINT notification_history_pkey PRIMARY KEY(id);
