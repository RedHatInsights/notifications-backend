CREATE TABLE endpoint_event_type (
         event_type_id UUID NOT NULL,
         endpoint_id UUID NOT NULL,
         CONSTRAINT pk_endpoint_event_type PRIMARY KEY (event_type_id, endpoint_id),
         CONSTRAINT fk_endpoint_event_type_event_type_id FOREIGN KEY (event_type_id) REFERENCES event_type (id) ON DELETE CASCADE,
         CONSTRAINT fk_endpoint_event_type_endpoint_id FOREIGN KEY (endpoint_id) REFERENCES endpoints (id) ON DELETE CASCADE
);
