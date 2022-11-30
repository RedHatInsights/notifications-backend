CREATE TABLE aggregation_org_config
(
    org_id                TEXT PRIMARY KEY,
    scheduled_execution_time time DEFAULT '00:00:00' NOT NULL,
    last_run              timestamp
);
