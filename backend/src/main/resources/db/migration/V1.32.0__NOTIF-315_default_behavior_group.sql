ALTER TABLE behavior_group
    -- ADD COLUMN default_behavior BOOLEAN NOT NULL DEFAULT FALSE
    ALTER COLUMN account_id DROP NOT NULL;
