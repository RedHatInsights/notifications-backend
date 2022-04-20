-- ensure the sub_types are lower case

UPDATE endpoints
    SET   endpoint_sub_type = LOWER(endpoint_sub_type)
    WHERE endpoint_sub_type != LOWER(endpoint_sub_type);

UPDATE notification_history
    SET   endpoint_sub_type = LOWER(endpoint_sub_type)
    WHERE endpoint_sub_type != LOWER(endpoint_sub_type)
