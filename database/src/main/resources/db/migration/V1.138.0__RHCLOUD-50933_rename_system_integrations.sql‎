-- Rename existing email and drawer integrations from the old naming convention
-- ("Email endpoint <UUID>" / "Drawer endpoint <UUID>") to the new one
-- ("Email integration", "Email integration 1", "Email integration 2", ...).
-- Only integrations with a non-null org_id are renamed.
--
-- NOTE: This changes the endpoint naming convention visible through the API.
-- Clients that parse endpoint names should expect the new format.
--
-- The row-by-row approach is acceptable here because the number of affected
-- endpoints is well below 10,000 across all organizations.
--
-- Flyway's migrate-at-start guarantees this migration runs before the new
-- application code, so there is no risk of concurrent name assignment.

DO $$
DECLARE
    rec RECORD;
    base_name TEXT;
    new_name TEXT;
    suffix INT;
BEGIN
    FOR rec IN
        SELECT id, org_id, endpoint_type_v2
        FROM endpoints
        WHERE org_id IS NOT NULL
          AND endpoint_type_v2 IN ('EMAIL_SUBSCRIPTION', 'DRAWER')
          AND (name LIKE 'Email endpoint %' OR name LIKE 'Drawer endpoint %')
        ORDER BY org_id, endpoint_type_v2, created
    LOOP
        base_name := CASE rec.endpoint_type_v2
            WHEN 'DRAWER' THEN 'Drawer integration'
            ELSE 'Email integration'
        END;

        -- Find the next available name for this org
        IF NOT EXISTS (
            SELECT 1 FROM endpoints
            WHERE org_id = rec.org_id AND name = base_name
        ) THEN
            new_name := base_name;
        ELSE
            suffix := 1;
            LOOP
                new_name := base_name || ' ' || suffix;
                EXIT WHEN NOT EXISTS (
                    SELECT 1 FROM endpoints
                    WHERE org_id = rec.org_id AND name = new_name
                );
                suffix := suffix + 1;
            END LOOP;
        END IF;

        UPDATE endpoints SET name = new_name WHERE id = rec.id;
    END LOOP;
END $$;
