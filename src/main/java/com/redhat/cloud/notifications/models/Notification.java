package com.redhat.cloud.notifications.models;

public class Notification {
    private final String tenant;

    // TODO This is a placeholder!
    private final Object payload;

    private final Endpoint endpoint;

    public Notification(String tenant, Object payload, Endpoint endpoint) {
        this.tenant = tenant;
        this.payload = payload;
        this.endpoint = endpoint;
    }

    public String getTenant() {
        return tenant;
    }

    public Object getPayload() {
        return payload;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }
}
