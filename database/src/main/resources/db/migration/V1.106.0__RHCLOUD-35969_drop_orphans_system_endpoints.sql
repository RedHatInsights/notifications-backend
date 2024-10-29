DELETE FROM endpoints WHERE org_id is null and NOT EXISTS (SELECT 1 FROM behavior_group_action bga WHERE bga.endpoint_id = id);
