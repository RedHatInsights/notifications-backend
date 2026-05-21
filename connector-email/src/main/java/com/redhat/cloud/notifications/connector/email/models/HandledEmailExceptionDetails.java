package com.redhat.cloud.notifications.connector.email.models;

import com.redhat.cloud.notifications.connector.v2.models.HandledExceptionDetails;

public class HandledEmailExceptionDetails extends HandledExceptionDetails {
    public String additionalErrorDetails;

    public HandledEmailExceptionDetails(HandledExceptionDetails processedExceptionDetails) {
        super(processedExceptionDetails.outcomeMessage);
    }
}
