CREATE TABLE email_properties (
    only_admins boolean NOT NULL,
    ignore_preferences boolean NOT NULL,
    group_id uuid,
    id uuid NOT NULL,
    CONSTRAINT pk_email_properties PRIMARY KEY (id),
    CONSTRAINT fk_email_properties_endpoint_id FOREIGN KEY (id) REFERENCES endpoints(id) ON DELETE CASCADE
);

INSERT INTO email_properties (id, only_admins, ignore_preferences, group_id) SELECT id, false, false, null from endpoints WHERE endpoint_type = 1;
