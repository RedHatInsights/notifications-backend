package com.redhat.cloud.notifications.connector.drawer.models;

import com.redhat.cloud.notifications.connector.v2.http.models.HandledHttpExceptionDetails;

public class HandledDrawerExceptionDetails extends HandledHttpExceptionDetails {
    public String additionalErrorDetails;

    public HandledDrawerExceptionDetails(HandledHttpExceptionDetails processedExceptionDetails) {
        super(processedExceptionDetails.outcomeMessage, processedExceptionDetails.httpStatusCode);
    }
}
