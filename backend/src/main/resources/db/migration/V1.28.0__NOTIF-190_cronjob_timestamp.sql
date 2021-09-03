CREATE TABLE cronjob_run (
    last_run TIMESTAMP,
    -- The following PK and CHECK constraints combination guarantees that the table will never contain more than one row.
    prevent_multiple_rows BOOLEAN PRIMARY KEY DEFAULT TRUE,
    CONSTRAINT cronjob_run_table_should_never_contain_multiple_rows CHECK (prevent_multiple_rows)
);

INSERT INTO cronjob_run (last_run) VALUES ('08/20/1900 09:34:19.503344+0');
