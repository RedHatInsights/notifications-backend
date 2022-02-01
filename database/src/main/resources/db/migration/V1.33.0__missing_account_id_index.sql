-- Creates missing index for account_id column on event and behavior_group

CREATE INDEX ix_event_account_id ON event USING btree (account_id);
CREATE INDEX ix_behavior_group_account_id ON behavior_group USING btree (account_id);
