-- This is step 1 of the migration from endpoints types "ordinal" to "name".

ALTER TABLE endpoints
    ADD COLUMN endpoint_type_v2 VARCHAR(20);

ALTER TABLE notification_history
    ADD COLUMN endpoint_type_v2 VARCHAR(20);

-- TODO Remove this function after the migration is done.
CREATE FUNCTION transition_to_string_endpoint_type() RETURNS TRIGGER AS $$
BEGIN
    IF (NEW.endpoint_type = 0) THEN
        NEW.endpoint_type_v2 := 'WEBHOOK';
    ELSIF (NEW.endpoint_type = 1) THEN
        NEW.endpoint_type_v2 := 'EMAIL_SUBSCRIPTION';
    ELSIF (NEW.endpoint_type = 2) THEN
        NEW.endpoint_type_v2 := 'DEFAULT';
    ELSIF (NEW.endpoint_type = 3) THEN
        NEW.endpoint_type_v2 := 'CAMEL';
    END IF;
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

UPDATE endpoints SET endpoint_type_v2 =
    CASE
        WHEN endpoint_type = 0 THEN 'WEBHOOK'
        WHEN endpoint_type = 1 THEN 'EMAIL_SUBSCRIPTION'
        WHEN endpoint_type = 2 THEN 'DEFAULT'
        WHEN endpoint_type = 3 THEN 'CAMEL'
    END;

UPDATE notification_history SET endpoint_type_v2 =
    CASE
        WHEN endpoint_type = 0 THEN 'WEBHOOK'
        WHEN endpoint_type = 1 THEN 'EMAIL_SUBSCRIPTION'
        WHEN endpoint_type = 2 THEN 'DEFAULT'
        WHEN endpoint_type = 3 THEN 'CAMEL'
    END;

ALTER TABLE endpoints
    ALTER COLUMN endpoint_type_v2 SET NOT NULL,
    ALTER COLUMN endpoint_type DROP NOT NULL;

ALTER TABLE notification_history
    ALTER COLUMN endpoint_type_v2 SET NOT NULL,
    ALTER COLUMN endpoint_type DROP NOT NULL;
