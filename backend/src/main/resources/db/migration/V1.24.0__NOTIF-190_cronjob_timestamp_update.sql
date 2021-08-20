UPDATE cronjob_run
SET last_run = NOW()
WHERE last_run = '-infinity';