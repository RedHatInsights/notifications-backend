CREATE TABLE public.endpoint_camel (
    url character varying NOT NULL,
    sub_type varchar(50) NOT NULL,
    disable_ssl_verification boolean NOT NULL,
    secret_token character varying(255),
    basic_authentication text,
    id uuid NOT NULL,
    extras text
);


ALTER TABLE endpoint_camel
    ADD CONSTRAINT pk_endpoint_camel PRIMARY KEY (id);


ALTER TABLE endpoint_camel
    ADD CONSTRAINT fk_endpoint_camel_endpoint FOREIGN KEY (id) REFERENCES public.endpoints(id)
        ON DELETE CASCADE;


