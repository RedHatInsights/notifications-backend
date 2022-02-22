package com.redhat.cloud.notifications.temp.openbridge;

/**
 *
 */
public class Bridge {
    private String id;
    private String eventsEndpoint;

    public Bridge(String id, String eventsEndpoint) {
        this.id = id;
        this.eventsEndpoint = eventsEndpoint;
    }

    public String getId() {
        return id;
    }

    public String getEventsEndpoint() {
        return eventsEndpoint;
    }

}
