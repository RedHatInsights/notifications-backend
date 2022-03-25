package com.redhat.cloud.notifications.openbridge;

/**
 *
 */
public class Bridge {
    /* ID of the bridge is a unique identifier */
    private String id;
    /* The endpoint CloudEvents should be sent to */
    private String endpoint;
    /* The name of the bridge. */
    private String name;

    public Bridge(String id, String eventsEndpoint, String name) {
        this.id = id;
        this.endpoint = eventsEndpoint;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getEndpoint() {
        return endpoint;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Bridge{");
        sb.append("id='").append(id).append('\'');
        sb.append(", name='").append(name).append('\'');
        sb.append(", eventsEndpoint='").append(endpoint).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
