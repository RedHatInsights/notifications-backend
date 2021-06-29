CREATE TABLE status (
    value VARCHAR(11) NOT NULL,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    -- The following PK and CHECK constraints combination guarantees that the table will never contain more than one row.
    prevent_multiple_rows BOOLEAN PRIMARY KEY DEFAULT TRUE,
    CONSTRAINT status_table_should_never_contain_multiple_rows CHECK (prevent_multiple_rows)
) WITH (OIDS=FALSE);

INSERT INTO status (value) VALUES ('UP');
