CREATE TABLE aggregation_cronjob_parameter
(
    org_id                TEXT PRIMARY KEY,
    expected_running_time time DEFAULT '00:00:00' NOT NULL,
    last_run              timestamp
);
