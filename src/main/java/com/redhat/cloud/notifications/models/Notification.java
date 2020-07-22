package com.redhat.cloud.notifications.models;

public class Notification {
    private final String tenant;

    // TODO This is a placeholder!
    private final Object payload;

    public Notification(String tenant, Object payload) {
        this.tenant = tenant;
        this.payload = payload;
    }

    public String getTenant() {
        return tenant;
    }

    public Object getPayload() {
        return payload;
    }
}
