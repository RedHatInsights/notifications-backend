package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.Column;
import java.util.UUID;

public abstract class Attributes {
    @Column(name = "endpoint_id")
    @JsonIgnore
    // TODO Add to the Postgres that this must be unique FK (1:1)
    protected UUID endpointId; // To endpoints table

    public UUID getEndpointId() {
        return endpointId;
    }
}
