-- This script recreates a constraint to drop the ON DELETE and ON UPDATE clauses.
-- This is done using the NOT VALID option and the VALIDATE CONSTRAINT operation to speed up the schema migration.
-- See https://www.postgresql.org/docs/13/sql-altertable.html for more details about NOT VALID.

ALTER TABLE notification_history
    ADD COLUMN endpoint_type INTEGER;

UPDATE notification_history nh SET endpoint_type = (SELECT e.endpoint_type FROM endpoints e WHERE e.id = nh.endpoint_id);

ALTER TABLE notification_history
    ALTER COLUMN endpoint_type SET NOT NULL,
    ALTER COLUMN endpoint_id DROP NOT NULL,
    DROP CONSTRAINT notification_history_endpoint_id_fkey,
    ADD CONSTRAINT fk_notification_history_endpoint_id FOREIGN KEY (endpoint_id) REFERENCES endpoints (id) ON DELETE SET NULL NOT VALID;

ALTER TABLE notification_history
    VALIDATE CONSTRAINT fk_notification_history_endpoint_id;
