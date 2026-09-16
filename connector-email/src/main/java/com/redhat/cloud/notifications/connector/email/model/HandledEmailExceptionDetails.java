package com.redhat.cloud.notifications.connector.email.model;

import com.redhat.cloud.notifications.connector.v2.http.models.HandledHttpExceptionDetails;

public class HandledEmailExceptionDetails extends HandledHttpExceptionDetails {
    public String payloadId;

    public HandledEmailExceptionDetails(HandledHttpExceptionDetails source) {
        super(source.outcomeMessage, source.httpStatusCode);
        this.httpErrorType = source.httpErrorType;
        responseBody = source.responseBody;
    }
}
