package com.redhat.cloud.notifications.models;

import javax.persistence.Column;

public abstract class Attributes {
    @Column(name = "endpoint_id")
    // TODO Add to the Postgres that this must be unique FK (1:1)
    protected String endpointId; // To endpoints table

    public String getEndpointId() {
        return endpointId;
    }
}
