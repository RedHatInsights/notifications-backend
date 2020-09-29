-- Table: public.event_type

-- DROP TABLE public.event_type;

CREATE TABLE public.event_type
(
    id integer NOT NULL DEFAULT nextval('event_type_id_seq'::regclass),
    name character varying(255) COLLATE pg_catalog."default" NOT NULL,
    description character varying COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT "PK_event_type_id" PRIMARY KEY (id)
)
WITH (
    OIDS = FALSE
)
TABLESPACE pg_default;

-- Table: public.endpoint_targets

-- DROP TABLE public.endpoint_targets;

CREATE TABLE public.endpoint_targets
(
    account_id character varying(50) COLLATE pg_catalog."default" NOT NULL,
    event_type_id bigint NOT NULL,
    endpoint_id uuid NOT NULL,
    CONSTRAINT endpoint_targets_pkey PRIMARY KEY (account_id, event_type_id)
)
WITH (
    OIDS = FALSE
)
TABLESPACE pg_default;

ALTER TABLE public.endpoint_targets
    ADD CONSTRAINT "FK_event_type_id_target" FOREIGN KEY (event_type_id)
    REFERENCES public.event_type (id) MATCH SIMPLE
    ON UPDATE CASCADE
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE public.endpoint_targets
    ADD CONSTRAINT "FK_endpoint_id_target" FOREIGN KEY (endpoint_id)
    REFERENCES public.endpoints (id) MATCH SIMPLE
    ON UPDATE CASCADE
    ON DELETE CASCADE
    NOT VALID;

-- Table: public.application_event_type

-- DROP TABLE public.application_event_type;

CREATE TABLE public.application_event_type
(
    application_id uuid NOT NULL,
    event_type_id bigint NOT NULL,
    CONSTRAINT application_event_type_pkey PRIMARY KEY (event_type_id, application_id),
    CONSTRAINT "FK_application_id" FOREIGN KEY (application_id)
        REFERENCES public.applications (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID,
    CONSTRAINT "FK_event_type_id" FOREIGN KEY (event_type_id)
        REFERENCES public.event_type (id) MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE
        NOT VALID
)
WITH (
    OIDS = FALSE
)
TABLESPACE pg_default;
