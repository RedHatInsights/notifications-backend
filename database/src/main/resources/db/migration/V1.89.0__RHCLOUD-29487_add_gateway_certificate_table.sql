CREATE TABLE gateway_certificate (
    id UUID NOT NULL,
    application_id UUID NOT NULL,
    certificate_data text NOT NULL,
    environment text NOT NULL,
    CONSTRAINT pk_gateway_certificate_notification PRIMARY KEY (id)
);

-- gateway_certificate foreign key
ALTER TABLE gateway_certificate ADD CONSTRAINT fk_gateway_certificate_application_id FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE;
