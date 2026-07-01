DELETE FROM endpoints ep
WHERE ep.endpoint_type_v2 = 'EMAIL_SUBSCRIPTION'
    AND NOT EXISTS (SELECT 1 FROM endpoint_event_type eet WHERE eet.endpoint_id = ep.id)
    AND NOT EXISTS (SELECT 1 FROM behavior_group_action bga WHERE bga.endpoint_id = ep.id)
    AND NOT EXISTS (SELECT 1 FROM notification_history nh WHERE nh.endpoint_id = ep.id);
