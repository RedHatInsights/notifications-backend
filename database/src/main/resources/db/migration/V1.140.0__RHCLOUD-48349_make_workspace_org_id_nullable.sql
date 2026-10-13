-- RHCLOUD-48349: Workspace bootstrap support
--
-- This migration enables workspace bootstrap by:
-- 1. Making workspace.org_id nullable to support the system workspace
-- 2. Adding a unique constraint to ensure only one system workspace exists
-- 3. Creating the system workspace with a well-known UUID
-- 4. Assigning all existing system endpoints to the system workspace
--
-- The system workspace (org_id IS NULL) is used for platform integrations
-- that don't belong to any specific organization.
--
-- The system workspace UUID is deterministically generated from the string
-- "notifications-system-workspace" using UUID v3 (MD5-based).

-- Allow org_id to be NULL for the system workspace
ALTER TABLE workspace ALTER COLUMN org_id DROP NOT NULL;

-- Ensure only one system workspace can exist (org_id IS NULL)
-- The partial unique index on (1) WHERE org_id IS NULL means at most one row
-- can satisfy the WHERE predicate, enforcing single system workspace constraint
CREATE UNIQUE INDEX workspace_unique_system_workspace ON workspace((1)) WHERE org_id IS NULL;

-- Create the system workspace with well-known UUID
-- This UUID matches: UUID.nameUUIDFromBytes("notifications-system-workspace".getBytes(UTF_8))
INSERT INTO workspace (id, org_id, created)
VALUES ('5d3d4d41-a502-36a6-b724-3b1fcfe41a1a', NULL, NOW());

-- Assign all existing system endpoints (org_id IS NULL) to the system workspace
UPDATE endpoint
SET workspace_id = '5d3d4d41-a502-36a6-b724-3b1fcfe41a1a'
WHERE org_id IS NULL;
