-- We dont have any value in email_aggregation, is OK to set NO NULL.
ALTER TABLE public.email_aggregation ADD COLUMN application_id uuid NOT NULL;

-- insight_id is part of the payload and specific to policies aggregation.
ALTER TABLE public.email_aggregation DELETE COLUMN insight_id;
