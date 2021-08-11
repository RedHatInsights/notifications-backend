CREATE TABLE cronjob_run (
    id UUID NOT NULL,
    last_run TIMESTAMP
) WITH (OIDS=FALSE);

INSERT INTO cronjob_run (id, last_run) VALUES
('3db0955b-751b-48cd-b531-c1d81596d133', '-infinity');
