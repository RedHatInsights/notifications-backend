CREATE TABLE x509_certificate (
    id UUID NOT NULL,
    application_id UUID NOT NULL,
    subject_dn text NOT NULL,
    source_environment text NOT NULL,
    CONSTRAINT pk_x509_certificate PRIMARY KEY (id),
    CONSTRAINT uq_x509_certificate UNIQUE (application_id, subject_dn, source_environment)
);

-- gateway_certificate foreign key
ALTER TABLE x509_certificate ADD CONSTRAINT fk_x509_certificate_application_id FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE;
