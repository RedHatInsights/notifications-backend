-- RHCLOUD-48349: Make workspace.org_id nullable to support system workspace
-- The system workspace is used for endpoints without an org_id (e.g., default integrations).

ALTER TABLE workspace ALTER COLUMN org_id DROP NOT NULL;

-- Add index on endpoints.workspace_id for efficient queries and joins
-- This foreign key is frequently used in workspace-based filtering and reporting
CREATE INDEX IF NOT EXISTS idx_endpoints_workspace_id ON endpoints(workspace_id);
