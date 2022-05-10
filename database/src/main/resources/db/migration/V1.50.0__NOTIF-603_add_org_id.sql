ALTER TABLE email_aggregation
  ADD COLUMN org_id text;

CREATE INDEX email_aggregation_idx ON public.email_aggregation (org_id);

ALTER TABLE event
  ADD COLUMN org_id text;

CREATE INDEX event_idx ON public.event (org_id);
