DROP INDEX "IX_time_search_account_mails";

ALTER TABLE email_aggregation
    DROP COLUMN account_id;
