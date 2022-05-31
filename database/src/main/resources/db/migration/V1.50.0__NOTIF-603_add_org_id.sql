ALTER TABLE email_aggregation
  ADD COLUMN org_id text;

CREATE INDEX ix_email_aggregation_org_id ON public.email_aggregation (org_id);

ALTER TABLE event
  ADD COLUMN org_id text;

CREATE INDEX ix_event_org_id ON public.event (org_id);

ALTER TABLE behavior_group
  ADD COLUMN org_id text;

CREATE INDEX ix_behavior_group_org_id ON public.behavior_group (org_id);

ALTER TABLE endpoints
  ADD COLUMN org_id text;

CREATE INDEX ix_endpoints_org_id ON public.endpoints (org_id);
