CREATE TABLE aggregation_org_config
(
    org_id                TEXT PRIMARY KEY,
    scheduled_execution_time time NOT NULL,
    last_run              timestamp
);
