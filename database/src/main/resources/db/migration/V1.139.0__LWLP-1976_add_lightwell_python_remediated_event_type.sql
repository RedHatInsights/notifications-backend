-- LWLP-1976: Add the "python-remediated" event type under the existing
-- Lightwell bundle/application so Python remediated repository advisories
-- can be published through the same BAET as "java-remediated". The shared
-- app-level email template (eventType = null in Lightwell.java) already
-- renders any event type under lightwell/lightwell, so no template changes
-- are required alongside this row.
INSERT INTO event_type (id, application_id, name, display_name, description, default_severity, available_severities, visible, included_in_drawer)
SELECT gen_random_uuid(), a.id,
  'python-remediated',
  'Python Remediated',
  'Python Remediated',
  'NONE',
  json_build_array('LOW', 'CRITICAL', 'NONE', 'MODERATE', 'IMPORTANT'),
  TRUE,
  FALSE
FROM applications a INNER JOIN bundles b ON a.bundle_id = b.id
WHERE a.name = 'lightwell' AND b.name = 'lightwell'
ON CONFLICT DO NOTHING;
