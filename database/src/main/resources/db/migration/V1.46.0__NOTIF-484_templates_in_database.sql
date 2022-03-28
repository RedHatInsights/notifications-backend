CREATE TABLE template (
    id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    data VARCHAR NOT NULL,
    created TIMESTAMP NOT NULL,
    updated TIMESTAMP,
    CONSTRAINT pk_template PRIMARY KEY (id)
);

CREATE TABLE instant_email_template (
   event_type_id UUID NOT NULL,
   subject_template_id UUID NOT NULL,
   body_template_id UUID NOT NULL,
   created TIMESTAMP NOT NULL,
   updated TIMESTAMP,
   CONSTRAINT pk_instant_email_template PRIMARY KEY (event_type_id),
   CONSTRAINT fk_instant_email_template_event_type_id FOREIGN KEY (event_type_id) REFERENCES event_type (id) ON DELETE CASCADE,
   CONSTRAINT fk_instant_email_template_subject_template_id FOREIGN KEY (subject_template_id) REFERENCES template (id) ON DELETE CASCADE,
   CONSTRAINT fk_instant_email_template_body_template_id FOREIGN KEY (body_template_id) REFERENCES template (id) ON DELETE CASCADE
);

CREATE TABLE aggregation_email_template (
    application_id UUID NOT NULL,
    subscription_type VARCHAR(50) NOT NULL,
    subject_template_id UUID NOT NULL,
    body_template_id UUID NOT NULL,
    created TIMESTAMP NOT NULL,
    updated TIMESTAMP,
    CONSTRAINT pk_aggregation_email_template PRIMARY KEY (application_id, subscription_type),
    CONSTRAINT fk_aggregation_email_template_application_id FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT fk_aggregation_email_template_subject_template_id FOREIGN KEY (subject_template_id) REFERENCES template (id) ON DELETE CASCADE,
    CONSTRAINT fk_aggregation_email_template_body_template_id FOREIGN KEY (body_template_id) REFERENCES template (id) ON DELETE CASCADE
);
