package com.redhat.cloud.notifications.connector.v2.models;

public class HandledMessageDetails {
    public String outcomeMessage;

    public HandledMessageDetails() {
        // empty
    }

    public HandledMessageDetails(String outcomeMessage) {
        this.outcomeMessage = outcomeMessage;
    }
}
