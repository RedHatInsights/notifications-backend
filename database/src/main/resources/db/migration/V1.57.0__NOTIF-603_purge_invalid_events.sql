-- This query will remove invalid events from the DB. They were created
-- because one of the onboarded apps sent us by mistake Kafka messages
-- with an empty account_id field for a while.
DELETE FROM event WHERE account_id = '';
