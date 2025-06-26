ALTER TABLE endpoint_webhooks
    DROP COLUMN basic_authentication_id;

ALTER TABLE camel_properties
    DROP COLUMN basic_authentication_id;
