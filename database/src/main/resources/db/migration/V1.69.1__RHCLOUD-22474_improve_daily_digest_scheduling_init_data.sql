insert into "aggregation_cronjob_parameter" ("org_id", "last_run")
VALUES ('NA', (select last_run from "cronjob_run"));
