ALTER TABLE integration_template
    ADD COLUMN org_id TEXT;

CREATE INDEX ix_integration_template_org_id ON integration_template (org_id);
