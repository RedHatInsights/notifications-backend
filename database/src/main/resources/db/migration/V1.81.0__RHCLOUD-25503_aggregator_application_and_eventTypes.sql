DO $$
DECLARE aggregatorApplicationId UUID;
BEGIN

aggregatorApplicationId := (select ap.id from applications ap inner join bundles b on ap.bundle_id =b.id where ap.name = 'integrations' and b.name = 'console');

INSERT INTO event_type (name, display_name, application_id, id) VALUES
('aggregation', 'Daily digest', aggregatorApplicationId, gen_random_uuid());
END $$
