ALTER TABLE behavior_group
    ADD COLUMN locked_by_application_id UUID,
    ALTER COLUMN account_id DROP NOT NULL,
    ADD CONSTRAINT fk_behavior_group_locked_by_application_id FOREIGN KEY (locked_by_application_id) REFERENCES applications (id),
    ADD CONSTRAINT ck_should_be_locked_by_app_or_have_an_account_id
        -- The following line contains extra parenthesis to make the review easier.
        CHECK ((account_id IS NULL AND locked_by_application_id IS NOT NULL) OR (account_id IS NOT NULL AND locked_by_application_id IS NULL));
