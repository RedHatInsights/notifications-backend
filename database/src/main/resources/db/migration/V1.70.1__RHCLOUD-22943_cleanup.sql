-- This is step 3 of the migration from endpoints types "ordinal" to "name".

-- An old index needs to be recreated with the new column.
DROP INDEX ix_endpoints_type_sub_type;
CREATE INDEX ix_endpoints_type_sub_type ON endpoints (endpoint_type_v2, endpoint_sub_type);

-- Migration triggers and their associated function are no longer needed.
DROP TRIGGER endpoints_string_endpoint_type ON endpoints;
DROP TRIGGER notification_history_string_endpoint_type ON notification_history;
DROP FUNCTION transition_to_string_endpoint_type;

-- Old `endpoint_type` columns are no longer used.
ALTER TABLE endpoints DROP COLUMN endpoint_type;
ALTER TABLE notification_history DROP COLUMN endpoint_type;
