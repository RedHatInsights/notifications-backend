package com.redhat.cloud.notifications.models.endpoint.attributes;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;

public abstract class Attributes {
    @JsonIgnore
    // TODO Add to the Postgres that this must be unique FK (1:1)
    protected UUID endpointId; // To endpoints table

    public UUID getEndpointId() {
        return endpointId;
    }
}
