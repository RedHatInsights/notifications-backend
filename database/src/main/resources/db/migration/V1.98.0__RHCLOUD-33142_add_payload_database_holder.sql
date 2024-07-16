CREATE TABLE payload_details (
    id                      UUID                                                    NOT NULL,
    event_id                UUID                                                    NOT NULL,
    contents                TEXT                                                    NOT NULL,

    created                 TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc', now()) NOT NULL,

    CONSTRAINT pk_payload_details_id PRIMARY KEY (id),
    CONSTRAINT fk_payload_details_event_id FOREIGN KEY (event_id) REFERENCES event (id) ON DELETE CASCADE
);

COMMENT ON TABLE payload_details IS 'Holds payloads that are too big to be sent over Kafka';

COMMENT ON COLUMN payload_details.id IS 'The identifier of payload';

COMMENT ON COLUMN payload_details.event_id IS 'The reference to the event the payload is associated to';
COMMENT ON COLUMN payload_details.contents IS 'The contents of the generated payload for the event';

COMMENT ON COLUMN payload_details.created IS 'Creation timestamp for the row';
