DO $$
DECLARE aggregatorApplicationId UUID;
BEGIN

INSERT INTO applications (id, name, display_name, created, bundle_id) VALUES
    (gen_random_uuid(), 'notifications', 'Notifications', now(), (select id from bundles where name = 'console'))
RETURNING id INTO aggregatorApplicationId;

INSERT INTO event_type (name, display_name, application_id, id) VALUES
('aggregation', 'Daily digest', aggregatorApplicationId, gen_random_uuid());
END $$
