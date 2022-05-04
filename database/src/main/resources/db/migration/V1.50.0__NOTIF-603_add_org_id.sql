ALTER TABLE email_aggregation
  ADD COLUMN org_id text;

ALTER TABLE event
  ADD COLUMN org_id text;
