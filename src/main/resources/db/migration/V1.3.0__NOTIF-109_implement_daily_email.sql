ALTER TABLE public.email_aggregation
  -- We dont have any value in email_aggregation, is OK to set NO NULL.
  ADD COLUMN application character varying(255) NOT NULL,
  -- insight_id is part of the payload and specific to policies aggregation.
  DROP COLUMN insight_id;
