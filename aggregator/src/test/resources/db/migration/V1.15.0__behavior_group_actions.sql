ALTER TABLE behavior_group_action
    ADD COLUMN position INTEGER NOT NULL DEFAULT 0; -- The default value is required to migrate existing actions.

ALTER TABLE behavior_group_action
    ALTER COLUMN position DROP DEFAULT; -- Migration done, we can drop the default value.
