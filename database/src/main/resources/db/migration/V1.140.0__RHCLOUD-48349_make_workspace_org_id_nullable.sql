-- RHCLOUD-48349: Make workspace.org_id nullable to support system workspace
-- The system workspace is used for endpoints without an org_id (e.g., default integrations).

ALTER TABLE workspace ALTER COLUMN org_id DROP NOT NULL;
