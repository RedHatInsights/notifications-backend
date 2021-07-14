CREATE TABLE email_properties (
    only_admins boolean NOT NULL,
    ignore_preferences boolean NULL,
    group_id uuid,
    id uuid NOT NULL,
    CONSTRAINT pk_email_properties PRIMARY KEY (id),
    CONSTRAINT fk_email_properties_endpoint_id FOREIGN KEY (id) REFERENCES endpoints(id) ON DELETE CASCADE
);
