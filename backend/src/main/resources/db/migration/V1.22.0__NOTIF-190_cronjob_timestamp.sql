CREATE TABLE cronjob_run (
    id UUID NOT NULL,
    last_run TIMESTAMP
) WITH (OIDS=FALSE);