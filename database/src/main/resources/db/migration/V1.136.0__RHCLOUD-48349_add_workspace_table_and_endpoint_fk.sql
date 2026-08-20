-- RHCLOUD-48349: introduce the workspace table and a nullable workspace_id FK on endpoints.
-- Foundation for the workspace-aware backend (epic RHCLOUD-48347). This migration must be
-- deployed to production before any JPA entity references the new table/column, otherwise
-- Hibernate startup validation fails.

CREATE TABLE IF NOT EXISTS workspace (
    id UUID NOT NULL DEFAULT public.gen_random_uuid(),
    org_id TEXT NOT NULL,
    created timestamp with time zone DEFAULT now() NOT NULL,
    updated timestamp with time zone,
    CONSTRAINT pk_workspace PRIMARY KEY (id)
);

-- Nullable during the transition window only; a follow-up migration makes it NOT NULL once
-- every endpoint has been assigned a workspace.
ALTER TABLE endpoints ADD COLUMN IF NOT EXISTS workspace_id UUID;

-- SET NULL (not CASCADE): removing a workspace must not delete the endpoints referencing it.
ALTER TABLE endpoints ADD CONSTRAINT fk_endpoints_workspace_id
    FOREIGN KEY (workspace_id)
    REFERENCES workspace(id)
    ON DELETE SET NULL;
