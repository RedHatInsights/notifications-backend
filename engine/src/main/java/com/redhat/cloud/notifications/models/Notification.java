package com.redhat.cloud.notifications.models;

public class Notification {

    private final Event event;
    private final Endpoint endpoint;

    public Notification(Event event, Endpoint endpoint) {
        this.event = event;
        this.endpoint = endpoint;
    }

    public Event getEvent() {
        return event;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }
}
