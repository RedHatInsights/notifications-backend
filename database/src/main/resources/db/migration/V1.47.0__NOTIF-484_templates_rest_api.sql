ALTER TABLE template
    ADD COLUMN description VARCHAR NOT NULL;

ALTER TABLE instant_email_template
    DROP CONSTRAINT pk_instant_email_template,
    ADD COLUMN id UUID NOT NULL,
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ALTER COLUMN event_type_id DROP NOT NULL,
    ADD CONSTRAINT pk_instant_email_template PRIMARY KEY (id);

-- The following partial index guarantees that there will never be more
-- than one enabled instant email template linked with an event type.
CREATE UNIQUE INDEX ix_instant_email_template_enabled
    ON instant_email_template (event_type_id)
    WHERE enabled = TRUE;

ALTER TABLE aggregation_email_template
    DROP CONSTRAINT pk_aggregation_email_template,
    ADD COLUMN id UUID NOT NULL,
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ALTER COLUMN application_id DROP NOT NULL,
    ADD CONSTRAINT pk_aggregation_email_template PRIMARY KEY (id),
    ADD CONSTRAINT uq_aggregation_email_template_application_id_subscription_type UNIQUE (application_id, subscription_type);

-- The following partial index guarantees that there will never be more than one enabled
-- aggregation email template linked with an (event type, subscription_type) couple.
CREATE UNIQUE INDEX ix_aggregation_email_template_enabled
    ON aggregation_email_template (application_id, subscription_type)
    WHERE enabled = TRUE;
