--
-- Create a new table to store the internal roles
CREATE TABLE internal_role_access (
    id UUID NOT NULL,
    role VARCHAR(200) NOT NULL,
    application_id UUID,
    CONSTRAINT pk_internal_role_access PRIMARY KEY (id),
    CONSTRAINT uq_internal_role_role_access_application_id UNIQUE (role, application_id),
    CONSTRAINT fk_internal_role_access_application_id FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE
) WITH (OIDS=FALSE);
