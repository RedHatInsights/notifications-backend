DELETE FROM endpoints ep WHERE endpoint_type_v2='EMAIL_SUBSCRIPTION'
        AND NOT EXISTS (SELECT 1 FROM endpoint_event_type WHERE endpoint_id=id)
        AND NOT EXISTS (SELECT 1 FROM behavior_group_action WHERE ep.id=endpoint_id)
        AND NOT EXISTS (SELECT 1 FROM notification_history WHERE ep.id=endpoint_id);
