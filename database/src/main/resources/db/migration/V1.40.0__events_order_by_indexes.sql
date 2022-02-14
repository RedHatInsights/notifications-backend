-- This changes the following indexes scan type from 'Bitmap Index Scan' to 'Index Only Scan' which should be much
-- faster as Postgre won't need to check for dead tuples before returning the result.

DROP index ix_event_account_id_created_asc;

CREATE INDEX ix_event_service_index_only_scan_asc
    ON event (account_id, created, event_type_id, id);

DROP index ix_event_account_id_created_desc;

CREATE INDEX ix_event_service_index_only_scan_desc
    ON event (account_id, created DESC, event_type_id, id);
