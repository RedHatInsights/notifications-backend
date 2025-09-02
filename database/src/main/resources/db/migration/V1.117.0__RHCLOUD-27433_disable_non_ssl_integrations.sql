UPDATE endpoints
SET enabled = false
WHERE id IN (SELECT id
             FROM endpoint_webhooks
             WHERE disable_ssl_verification = true);

UPDATE endpoints
SET enabled = false
WHERE id IN (SELECT id
             FROM camel_properties
             WHERE disable_ssl_verification = true);

