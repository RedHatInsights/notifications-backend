package com.redhat.cloud.notifications.models;

public class Notification {
    private String tenant;
    private String endpointId;

    // TODO This is a placeholder!
    private Object payload;

    public Notification(String tenant, String endpointId, Object payload) {
        this.tenant = tenant;
        this.endpointId = endpointId;
        this.payload = payload;
    }

    public String getTenant() {
        return tenant;
    }

    public String getEndpointId() {
        return endpointId;
    }

    public Object getPayload() {
        return payload;
    }
}
