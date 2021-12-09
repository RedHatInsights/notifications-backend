ALTER TABLE behavior_group
    ALTER COLUMN account_id DROP NOT NULL;

ALTER TABLE endpoints
    ALTER COLUMN account_id DROP NOT NULL;
