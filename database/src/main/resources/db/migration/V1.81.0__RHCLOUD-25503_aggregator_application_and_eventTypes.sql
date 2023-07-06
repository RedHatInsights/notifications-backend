DO $$
DECLARE aggregatorApplicationId UUID;
BEGIN

INSERT INTO applications (id, name, display_name, created, bundle_id) VALUES
(gen_random_uuid(), 'aggregator', 'Aggregator', now(), (select id from bundles where name = 'console'))
RETURNING id INTO aggregatorApplicationId;

INSERT INTO event_type (name, display_name, application_id, id) VALUES
('aggregation-rhel-policies', 'Rhel-Policies aggregation triggered', aggregatorApplicationId, gen_random_uuid()),
('aggregation-rhel-advisor', 'Rhel-Advisor aggregation triggered', aggregatorApplicationId, gen_random_uuid()),
('aggregation-rhel-compliance', 'Rhel-Compliance aggregation triggered', aggregatorApplicationId, gen_random_uuid()),
('aggregation-rhel-drift', 'Rhel-Drift aggregation triggered', aggregatorApplicationId, gen_random_uuid()),
('aggregation-rhel-inventory', 'Rhel-Inventory aggregation triggered', aggregatorApplicationId, gen_random_uuid()),
('aggregation-rhel-patch', 'Rhel-Patch aggregation triggered', aggregatorApplicationId, gen_random_uuid()),
('aggregation-rhel-resource-optimization', 'Rhel-Resource-Optimization aggregation triggered', aggregatorApplicationId, gen_random_uuid()),
('aggregation-rhel-vulnerability', 'Rhel-Vulnerability aggregation triggered', aggregatorApplicationId, gen_random_uuid());

END $$
