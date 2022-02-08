-- Moves the sub_type to the endpoints table and removes it from the camel_properties

-- Adds subtype property for endpoints

ALTER TABLE endpoints ADD COLUMN endpoint_sub_type VARCHAR(20);
ALTER TABLE notification_history ADD COLUMN endpoint_sub_type VARCHAR(20);

CREATE INDEX ix_endpoints_type_sub_type ON endpoints (endpoint_type, endpoint_sub_type);

-- Migrate camel sub_types
UPDATE endpoints as e SET endpoint_sub_type = (SELECT sub_type FROM camel_properties WHERE id = e.id);

-- Populate notification_history endpoint_sub_type
UPDATE notification_history as n SET endpoint_sub_type = e.endpoint_sub_type FROM endpoints e WHERE e.id = n.endpoint_id;

-- Drop column
ALTER TABLE camel_properties DROP COLUMN sub_type;
