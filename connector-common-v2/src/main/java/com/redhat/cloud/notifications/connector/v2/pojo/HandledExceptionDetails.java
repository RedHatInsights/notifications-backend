package com.redhat.cloud.notifications.connector.v2.pojo;

public class HandledExceptionDetails extends HandledMessageDetails {
    public HandledExceptionDetails() {
    }

    public HandledExceptionDetails(boolean success, String outcomeMessage) {
        super(success, outcomeMessage);
    }
}
