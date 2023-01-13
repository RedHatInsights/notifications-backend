-- Insert a bundle, an application and an event type that will be specifically
-- used for test actions and events. A very specific value is given in the UUID
-- field so that it makes it easier to debug the event type.

INSERT INTO event_type(
    application_id,
    id,
    description,
    display_name,
    "name"
) VALUES (
    (SELECT id FROM applications WHERE "name"='integrations'),
    'd18f54ea-589c-4d6d-8744-837f357b5f5d',
    'A test event type whose only purpose is to be used when sending test actions and events to clients',
    'Integration Test',
    'integration-test'
);
