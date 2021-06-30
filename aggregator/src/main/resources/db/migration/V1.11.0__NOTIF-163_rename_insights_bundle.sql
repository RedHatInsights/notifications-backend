--
-- NOTIF-163
-- Rename the 'insights' bundle to 'rhel'
--

-- Change bundle name and display-name
UPDATE public.bundles
SET name = 'rhel', display_name = 'Red Hat Enterprise Linux'
WHERE
  name = 'insights'
;

-- Update email susbscriptions
UPDATE public.endpoint_email_subscriptions
SET bundle = 'rhel'
WHERE
  bundle = 'insights'
;

UPDATE public.email_aggregation
SET bundle = 'rhel'
WHERE
  bundle = 'insights'
;
