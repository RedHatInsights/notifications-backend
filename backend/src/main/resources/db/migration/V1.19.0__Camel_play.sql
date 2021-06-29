CREATE TABLE camel_properties (
    url character varying NOT NULL,
    sub_type varchar(50) NOT NULL,
    disable_ssl_verification boolean NOT NULL,
    secret_token character varying(255),
    basic_authentication text,
    id uuid NOT NULL,
    extras text,
    CONSTRAINT pk_camel_properties PRIMARY KEY (id),
    CONSTRAINT fk_camel_properties_endpoint_id FOREIGN KEY (id) REFERENCES endpoints(id) ON DELETE CASCADE
);
