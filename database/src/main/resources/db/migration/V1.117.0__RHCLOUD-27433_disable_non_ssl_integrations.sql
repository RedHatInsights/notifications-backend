UPDATE endpoint_webhooks
SET disable_ssl_verification = false
WHERE disable_ssl_verification = true;

UPDATE camel_properties
SET disable_ssl_verification = false
WHERE disable_ssl_verification = true;
