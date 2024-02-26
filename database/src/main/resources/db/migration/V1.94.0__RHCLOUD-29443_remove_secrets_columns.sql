ALTER TABLE
    "camel_properties"
DROP COLUMN
    "basic_authentication",
DROP COLUMN
    "secret_token";

ALTER TABLE
    "endpoint_webhooks"
DROP COLUMN
    "basic_authentication",
DROP COLUMN
    "secret_token";
