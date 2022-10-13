-- Create the columns for the IDs of the authentications that will be created in Sources.

ALTER TABLE
    endpoint_webhooks
ADD COLUMN
    basic_authentication_id BIGINT
;

ALTER TABLE
    endpoint_webhooks
ADD COLUMN
    secret_token_id BIGINT
;

ALTER TABLE
    camel_properties
ADD COLUMN
    basic_authentication_id BIGINT
;

ALTER TABLE
    camel_properties
ADD COLUMN
    secret_token_id BIGINT
;

-- Add a couple of comments to clarify what the columns will hold.

COMMENT ON COLUMN
    endpoint_webhooks.basic_authentication_id
IS
    'the ID of the basic authentication data that is stored in sources'
;

COMMENT ON COLUMN
    endpoint_webhooks.secret_token_id
IS
    'the ID of the secret token data that is stored in sources'
;

COMMENT ON COLUMN
    camel_properties.basic_authentication_id
    IS
        'the ID of the basic authentication data that is stored in sources'
;

COMMENT ON COLUMN
    camel_properties.secret_token_id
    IS
        'the ID of the secret token data that is stored in sources'
;
