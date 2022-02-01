CREATE TABLE kafka_message (
    id UUID NOT NULL,
    created TIMESTAMP NOT NULL,
    CONSTRAINT pk_kafka_message PRIMARY KEY (id)
);
