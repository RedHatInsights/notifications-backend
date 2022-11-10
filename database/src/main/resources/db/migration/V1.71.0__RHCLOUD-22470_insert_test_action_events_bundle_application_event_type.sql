-- Insert a bundle, an application and an event type that will be specifically
-- used for test actions and events.

INSERT INTO bundles(
    id,
    "name",
    display_name,
    created
) VALUES (
    'a2b19f7b-b380-4dfe-826d-8af55e269d20',
    'test-actions-events-bundle',
    'Test Actions and Events Bundle',
    now()
);

INSERT INTO applications(
    id,
    "name",
    display_name,
    created,
    bundle_id
) VALUES (
    '967ce8b4-a5a6-4cb0-8acb-03fc5bbc1251',
    'test-actions-events-application',
    'Test Actions and Events Application',
    now(),
    (SELECT id FROM bundles WHERE "name"='test-actions-events-bundle')
);

INSERT INTO event_type(
    application_id,
    id,
    description,
    display_name,
    "name"
) VALUES (
    (SELECT id FROM applications WHERE "name"='test-actions-events-application'),
    'd18f54ea-589c-4d6d-8744-837f357b5f5d',
    'A test event type whose only purpose is to be used when sending test actions and events to clients',
    'Test event type',
    'test-event-type'
);
