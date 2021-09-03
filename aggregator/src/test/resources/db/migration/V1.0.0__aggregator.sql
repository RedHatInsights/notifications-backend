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

CREATE TABLE cronjob_run (
    last_run TIMESTAMP,
    -- The following PK and CHECK constraints combination guarantees that the table will never contain more than one row.
    prevent_multiple_rows BOOLEAN PRIMARY KEY DEFAULT TRUE,
    CONSTRAINT cronjob_run_table_should_never_contain_multiple_rows CHECK (prevent_multiple_rows)
);

INSERT INTO cronjob_run (last_run) VALUES (NOW() - INTERVAL '15 hours');
