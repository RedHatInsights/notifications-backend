CREATE TABLE cronjob_run (
    id UUID NOT NULL,
    last_run TIMESTAMP,
    CONSTRAINT pk_cronjob_run PRIMARY KEY (id)
);

INSERT INTO cronjob_run (id, last_run) VALUES
('3db0955b-751b-48cd-b531-c1d81596d133', NOW() - INTERVAL '15 hours');