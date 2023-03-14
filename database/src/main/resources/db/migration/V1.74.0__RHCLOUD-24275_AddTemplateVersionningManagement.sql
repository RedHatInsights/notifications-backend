CREATE TABLE template_version (
	id uuid NOT NULL,
	parent_id uuid NOT NULL,
	"version" smallint NOT NULL,
	"data" varchar NOT NULL,
	created timestamp NOT NULL,
	updated timestamp NULL,
	CONSTRAINT pk_template_version PRIMARY KEY (id),
	FOREIGN KEY (parent_id) REFERENCES template(id) ON DELETE CASCADE
);

ALTER TABLE template
    add column template_current_version_id UUID,
    alter column "data" DROP NOT NULL,
    add constraint fk_generic_template_template_current_version_id FOREIGN KEY (template_current_version_id) REFERENCES template_version (id);

INSERT INTO template_version(id, parent_id, "data", version, created ) select gen_random_uuid(), id, "data", 0, created from template t where t.template_current_version_id is null;
UPDATE template t SET template_current_version_id = (SELECT tv.id FROM template_version tv where t.id = tv.parent_id) where t.template_current_version_id is null;