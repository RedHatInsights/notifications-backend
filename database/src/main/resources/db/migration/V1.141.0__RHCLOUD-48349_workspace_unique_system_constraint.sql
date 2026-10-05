-- RHCLOUD-48349: Add unique partial index to ensure only one system workspace (org_id IS NULL)
-- This prevents concurrent bootstrap requests from creating multiple system workspaces

-- Partial unique index using a constant: only one row where org_id IS NULL can exist
-- The constant '1' ensures at most one row satisfies the WHERE predicate
-- This enforces a single system workspace across the entire system
CREATE UNIQUE INDEX workspace_unique_system_workspace ON workspace((1)) WHERE org_id IS NULL;
