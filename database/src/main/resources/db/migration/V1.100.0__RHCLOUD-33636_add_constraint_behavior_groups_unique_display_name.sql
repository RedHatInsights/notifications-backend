ALTER TABLE behavior_group ADD CONSTRAINT behavior_group_bundle_id_display_name_org_id_key UNIQUE (bundle_id, display_name, org_id);
