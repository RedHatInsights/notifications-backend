-- RHCLOUD-48349: Workspace bootstrap support
--
-- This migration enables workspace bootstrap by:
-- 1. Making workspace.org_id nullable to support the system workspace
-- 2. Adding a unique constraint to ensure only one system workspace exists
--
-- The system workspace (org_id IS NULL) is used for platform integrations
-- that don't belong to any specific organization.

-- Allow org_id to be NULL for the system workspace
ALTER TABLE workspace ALTER COLUMN org_id DROP NOT NULL;

-- Ensure only one system workspace can exist (org_id IS NULL)
-- The partial unique index on (1) WHERE org_id IS NULL means at most one row
-- can satisfy the WHERE predicate, enforcing single system workspace constraint
CREATE UNIQUE INDEX workspace_unique_system_workspace ON workspace((1)) WHERE org_id IS NULL;
