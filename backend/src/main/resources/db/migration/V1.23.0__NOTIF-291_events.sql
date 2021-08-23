CREATE TABLE event (
   id UUID NOT NULL,
   account_id VARCHAR(50) NOT NULL,
   event_type_id UUID,
   payload TEXT,
   created TIMESTAMP NOT NULL,
   CONSTRAINT pk_event PRIMARY KEY (id),
   CONSTRAINT fk_event_event_type_id FOREIGN KEY (event_type_id) REFERENCES event_type (id) ON DELETE CASCADE
);

ALTER TABLE notification_history
    DROP COLUMN event_id,
    ADD COLUMN event_id UUID,
    ADD CONSTRAINT notification_history_event_id FOREIGN KEY (event_id) REFERENCES event (id) ON DELETE CASCADE;
