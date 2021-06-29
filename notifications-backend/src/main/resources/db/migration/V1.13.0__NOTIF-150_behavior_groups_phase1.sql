CREATE TABLE behavior_group (
    id UUID NOT NULL,
    account_id VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    display_name VARCHAR NOT NULL,
    bundle_id UUID NOT NULL,
    default_behavior BOOLEAN NOT NULL DEFAULT FALSE,
    created TIMESTAMP NOT NULL,
    updated TIMESTAMP,
    CONSTRAINT pk_behavior_group PRIMARY KEY (id),
    CONSTRAINT uq_behavior_group_name UNIQUE (account_id, name),
    CONSTRAINT fk_behavior_group_bundle_id FOREIGN KEY (bundle_id) REFERENCES bundles (id) ON DELETE CASCADE
) WITH (OIDS=FALSE);

CREATE TABLE event_type_behavior (
    event_type_id UUID NOT NULL,
    behavior_group_id UUID NOT NULL,
    created TIMESTAMP NOT NULL,
    CONSTRAINT pk_event_type_behavior PRIMARY KEY (event_type_id, behavior_group_id),
    CONSTRAINT fk_event_type_behavior_event_type_id FOREIGN KEY (event_type_id) REFERENCES event_type (id) ON DELETE CASCADE,
    CONSTRAINT fk_event_type_behavior_behavior_group_id FOREIGN KEY (behavior_group_id) REFERENCES behavior_group (id) ON DELETE CASCADE
) WITH (OIDS=FALSE);

CREATE TABLE behavior_group_action (
    behavior_group_id UUID NOT NULL,
    endpoint_id UUID NOT NULL,
    created TIMESTAMP NOT NULL,
    CONSTRAINT pk_behavior_group_action PRIMARY KEY (behavior_group_id, endpoint_id),
    CONSTRAINT fk_behavior_group_action_behavior_group_id FOREIGN KEY (behavior_group_id) REFERENCES behavior_group (id) ON DELETE CASCADE,
    CONSTRAINT fk_behavior_group_action_endpoint_id FOREIGN KEY (endpoint_id) REFERENCES endpoints (id) ON DELETE CASCADE
) WITH (OIDS=FALSE);
