-- This is step 1 of the migration from endpoints types "ordinal" to "name".

ALTER TABLE endpoints
    ADD COLUMN endpoint_type_v2 VARCHAR(20);

ALTER TABLE notification_history
    ADD COLUMN endpoint_type_v2 VARCHAR(20);

-- TODO Remove this function after the migration is done.
CREATE FUNCTION transition_to_string_endpoint_type() RETURNS TRIGGER AS $$
BEGIN
    -- The endpoint type will be copied into the new column every time a row is inserted or updated.
    NEW.endpoint_type_v2 := NEW.endpoint_type;
    RETURN NEW;
END
$$ LANGUAGE PLPGSQL;

-- TODO Remove this trigger after the migration is done.
CREATE TRIGGER endpoints_string_endpoint_type
    BEFORE INSERT OR UPDATE ON endpoints
    FOR EACH ROW EXECUTE PROCEDURE transition_to_string_endpoint_type();

-- TODO Remove this trigger after the migration is done.
CREATE TRIGGER notification_history_string_endpoint_type
    BEFORE INSERT OR UPDATE ON notification_history
    FOR EACH ROW EXECUTE PROCEDURE transition_to_string_endpoint_type();

-- Updates all existing rows.
UPDATE endpoints SET endpoint_type_v2 = endpoint_type;
UPDATE notification_history SET endpoint_type_v2 = endpoint_type;

ALTER TABLE endpoints
    ALTER COLUMN endpoint_type_v2 SET NOT NULL,
    ALTER COLUMN endpoint_type DROP NOT NULL;

ALTER TABLE notification_history
    ALTER COLUMN endpoint_type_v2 SET NOT NULL,
    ALTER COLUMN endpoint_type DROP NOT NULL;
