ALTER TABLE email_properties ADD COLUMN group_ids TEXT;

-- Populate with existing group_id values as JSON array
UPDATE email_properties SET group_ids = json_build_array(group_id)::text WHERE group_id IS NOT NULL;
