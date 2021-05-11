-- This script changes the 'endpoint_webhooks' PK type from INTEGER to UUID.
-- The new 'endpoint_webhooks' PK value mirrors the 'endpoint' PK value.
-- This is a necessary step to improve the @OneToOne mapping between EndpointWebhook and Endpoint and ultimately support multiple endpoints properties types.
-- See https://vladmihalcea.com/the-best-way-to-map-a-onetoone-relationship-with-jpa-and-hibernate/ for more details about the @OneToOne improvement.

-- First, we need to drop the existing constraints and index from 'endpoint_webhooks'.
ALTER TABLE endpoint_webhooks
    DROP CONSTRAINT endpoint_webhooks_pkey,
    DROP CONSTRAINT endpoint_webhooks_endpoint_id_fkey;
DROP INDEX ix_endpoint_webhooks_endpoint_id;

-- Then, the 'id' is recreated with a different type (INTEGER -> UUID).
ALTER TABLE endpoint_webhooks
    DROP COLUMN id,
    ADD COLUMN id UUID;

-- The 'endpoint_webhook' -> 'endpoint' FK value will be the 'endpoint_webhook' PK value from now on.
UPDATE endpoint_webhooks SET id = endpoint_id;

-- Finally, we no longer need the 'endpoint_id column' since its values were copied into the 'id' column.
-- The constraints must also be restored. The same column is now used for both PK and FK.
ALTER TABLE endpoint_webhooks
    DROP COLUMN endpoint_id,
    ALTER COLUMN id SET NOT NULL,
    ADD CONSTRAINT pk_endpoint_webhooks PRIMARY KEY(id),
    ADD CONSTRAINT fk_endpoint_webhooks_endpoint FOREIGN KEY(id) REFERENCES endpoints(id) ON DELETE CASCADE;
