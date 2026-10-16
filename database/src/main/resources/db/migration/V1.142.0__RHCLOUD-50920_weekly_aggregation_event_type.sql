INSERT INTO event_type (name, display_name, application_id, id, default_severity, available_severities)
SELECT 'weekly-aggregation', 'Weekly Digest', id, gen_random_uuid(), 'LOW', json_build_array('LOW')
FROM applications
WHERE name = 'notifications' AND bundle_id = (SELECT id FROM bundles WHERE name = 'console');
