INSERT INTO event_type (id, application_id, name, display_name)
SELECT gen_random_uuid(), a.id , 'integration-disabled', 'Integration disabled'
FROM applications a, bundles b
WHERE a.bundle_id = b.id AND a.name = 'notifications' AND b.name = 'console'
ON CONFLICT DO NOTHING;
