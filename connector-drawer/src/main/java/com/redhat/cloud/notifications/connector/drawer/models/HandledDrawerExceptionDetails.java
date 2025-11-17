package com.redhat.cloud.notifications.connector.drawer.models;

import com.redhat.cloud.notifications.connector.v2.models.HandledExceptionDetails;

public class HandledDrawerExceptionDetails extends HandledExceptionDetails {
    public String additionalErrorDetails;

    public HandledDrawerExceptionDetails(HandledExceptionDetails processedExceptionDetails) {
        super(processedExceptionDetails.outcomeMessage);
    }
}
