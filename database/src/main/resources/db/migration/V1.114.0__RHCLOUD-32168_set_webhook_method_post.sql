UPDATE
    endpoint_webhooks
SET
    "method" = 'POST'
WHERE
    "method" != 'POST';
