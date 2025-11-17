package com.redhat.cloud.notifications.connector.v2.models;

public class HandledExceptionDetails extends HandledMessageDetails {
    public HandledExceptionDetails() {
    }

    public HandledExceptionDetails(String outcomeMessage) {
        super(outcomeMessage);
    }
}
