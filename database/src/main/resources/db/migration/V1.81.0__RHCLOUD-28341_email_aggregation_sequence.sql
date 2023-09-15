ALTER SEQUENCE email_aggregation_id_seq
    INCREMENT BY 50;

LOCK TABLE email_aggregation IN EXCLUSIVE MODE;
SELECT SETVAL('email_aggregation_id_seq', (SELECT MAX(id) + 1 FROM email_aggregation), false);
