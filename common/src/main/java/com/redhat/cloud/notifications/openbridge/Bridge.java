package com.redhat.cloud.notifications.openbridge;

/**
 *
 */
public class Bridge {
    private String id;
    private String eventsEndpoint;
    private String name;

    public Bridge(String id, String eventsEndpoint, String name) {
        this.id = id;
        this.eventsEndpoint = eventsEndpoint;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getEventsEndpoint() {
        return eventsEndpoint;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Bridge{");
        sb.append("id='").append(id).append('\'');
        sb.append(", name='").append(name).append('\'');
        sb.append(", eventsEndpoint='").append(eventsEndpoint).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
