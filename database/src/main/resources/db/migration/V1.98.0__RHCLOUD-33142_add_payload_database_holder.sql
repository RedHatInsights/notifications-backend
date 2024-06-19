CREATE TABLE payload_details (
    id                      UUID                                                    NOT NULL,
    org_id                  TEXT                                                    NOT NULL,
    contents                TEXT                                                    NOT NULL,

    created                 TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc', now()) NOT NULL,

    CONSTRAINT pk_payload_details_id PRIMARY KEY (id)
);

COMMENT ON TABLE payload_details IS 'Holds payloads that are too big to be sent over Kafka';

COMMENT ON COLUMN payload_details.id IS 'The identifier of payload';

COMMENT ON COLUMN payload_details.org_id IS 'The organization the event was generated for';
COMMENT ON COLUMN payload_details.contents IS 'The contents of the generated payload for the event';

COMMENT ON COLUMN payload_details.created IS 'Creation timestamp for the row';
