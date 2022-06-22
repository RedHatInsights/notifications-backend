ALTER TABLE template
    ADD COLUMN description VARCHAR NOT NULL;

ALTER TABLE instant_email_template
    DROP CONSTRAINT pk_instant_email_template,
    ADD COLUMN id UUID NOT NULL,
    ALTER COLUMN event_type_id DROP NOT NULL,
    ADD CONSTRAINT pk_instant_email_template PRIMARY KEY (id),
    ADD CONSTRAINT uq_instant_email_template_event_type_id UNIQUE (event_type_id);

ALTER TABLE aggregation_email_template
    DROP CONSTRAINT pk_aggregation_email_template,
    ADD COLUMN id UUID NOT NULL,
    ALTER COLUMN application_id DROP NOT NULL,
    ADD CONSTRAINT pk_aggregation_email_template PRIMARY KEY (id),
    ADD CONSTRAINT uq_aggregation_email_template_application_id_subscription_type UNIQUE (application_id, subscription_type);
