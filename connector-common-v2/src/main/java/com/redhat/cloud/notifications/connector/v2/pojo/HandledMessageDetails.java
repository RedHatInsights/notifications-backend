package com.redhat.cloud.notifications.connector.v2.pojo;

public class HandledMessageDetails {
    public boolean success;
    public String outcomeMessage;

    public HandledMessageDetails() {
        // empty
    }

    public HandledMessageDetails(boolean success, String outcomeMessage) {
        this.success = success;
        this.outcomeMessage = outcomeMessage;
    }
}
