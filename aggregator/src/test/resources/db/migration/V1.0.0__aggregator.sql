CREATE TABLE bundles (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    display_name VARCHAR NOT NULL,
    created TIMESTAMP NOT NULL,
    updated TIMESTAMP,
    CONSTRAINT pk_bundles PRIMARY KEY (id),
    CONSTRAINT uq_bundles_name UNIQUE (name)
);

CREATE TABLE applications (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    display_name VARCHAR NOT NULL,
    created TIMESTAMP NOT NULL,
    updated TIMESTAMP,
    bundle_id UUID,
    CONSTRAINT pk_applications PRIMARY KEY (id),
    CONSTRAINT fk_applications_bundle_id FOREIGN KEY (bundle_id) REFERENCES bundles(id) ON DELETE CASCADE,
    CONSTRAINT uq_applications_bundle_id_name UNIQUE (bundle_id, name)
);

CREATE SEQUENCE email_aggregation_id_seq
    AS INTEGER
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE email_aggregation (
    id INTEGER NOT NULL DEFAULT nextval('email_aggregation_id_seq'::regclass),
    account_id VARCHAR(50) NOT NULL,
    created TIMESTAMP NOT NULL,
    payload TEXT NOT NULL,
    application VARCHAR(255) NOT NULL,
    bundle VARCHAR(255) NOT NULL,
    CONSTRAINT pk_email_aggregation PRIMARY KEY (id),
    CONSTRAINT uq_email_aggregation_account_id_created UNIQUE (account_id, created)
);

CREATE TABLE endpoint_email_subscriptions (
    account_id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    subscription_type VARCHAR(50) NOT NULL,
    application_id UUID NOT NULL,
    CONSTRAINT pk_endpoint_email_subscriptions PRIMARY KEY (account_id, user_id, subscription_type, application_id),
    CONSTRAINT fk_endpoint_email_subscriptions_application_id FOREIGN KEY (application_id) REFERENCES applications(id)
);

INSERT INTO bundles (id, name, display_name, created) VALUES
('afdc8d77-fcd3-44ee-9103-501d8d1a71de', 'rhel', 'Red Hat Enterprise Linux', '2021-07-23 10:58:26.797023');

INSERT INTO applications (id, name, display_name, created, bundle_id) VALUES
('3db0955b-751b-48cd-b531-c1d81596d868', 'policies', 'Policies', '2021-07-23 10:58:26.716988', 'afdc8d77-fcd3-44ee-9103-501d8d1a71de');
