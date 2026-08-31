-- RHCLOUD-48349: introduce the workspace table and a nullable workspace_id FK on endpoints.
-- Foundation for the workspace-aware backend (epic RHCLOUD-48347). This migration must be
-- deployed to production before any JPA entity references the new table/column, otherwise
-- Hibernate startup validation fails.

CREATE TABLE IF NOT EXISTS workspace (
    id UUID NOT NULL DEFAULT public.gen_random_uuid(),
    org_id TEXT NOT NULL,
    created timestamp DEFAULT now() NOT NULL,
    updated timestamp,
    CONSTRAINT pk_workspace PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS ix_workspace_org_id ON workspace (org_id);

-- Nullable during the transition window only; a follow-up migration makes it NOT NULL once
-- every endpoint has been assigned a workspace.
ALTER TABLE endpoints ADD COLUMN IF NOT EXISTS workspace_id UUID;

-- SET NULL (not CASCADE): removing a workspace must not delete the endpoints referencing it.
ALTER TABLE endpoints ADD CONSTRAINT fk_endpoints_workspace_id
    FOREIGN KEY (workspace_id)
    REFERENCES workspace(id)
    ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS ix_endpoints_workspace_id ON endpoints (workspace_id);
