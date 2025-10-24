package com.redhat.cloud.notifications.connector.drawer.model;

import com.redhat.cloud.notifications.connector.v2.pojo.HandledExceptionDetails;

public class HandledDrawerExceptionDetails extends HandledExceptionDetails {
    public String additionalErrorDetails;

    public HandledDrawerExceptionDetails(HandledExceptionDetails processedExceptionDetails) {
        super(processedExceptionDetails.success, processedExceptionDetails.outcomeMessage);
    }
}
