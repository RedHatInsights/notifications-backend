-- Insert an event type that will specifically be used for test actions and
-- events.

INSERT INTO event_type(
    application_id,
    id,
    description,
    display_name,
    "name"
) VALUES (
    (SELECT a.id FROM applications AS a INNER JOIN bundles AS b ON b.id = a.bundle_id WHERE a."name"='integrations' AND b."name"='console'),
    gen_random_uuid(),
    'A test event type whose only purpose is to be used when sending test actions and events to clients',
    'Integration Test',
    'integration-test'
);
