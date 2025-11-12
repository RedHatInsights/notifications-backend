package com.redhat.cloud.notifications.connector.v2.http.models;

import com.redhat.cloud.notifications.connector.v2.http.HttpErrorType;
import com.redhat.cloud.notifications.connector.v2.models.HandledExceptionDetails;

public class HandledHttpExceptionDetails extends HandledExceptionDetails {
    public Integer httpStatusCode;
    public HttpErrorType httpErrorType;
    public String targetUrl;

    public HandledHttpExceptionDetails() {
    }

    public HandledHttpExceptionDetails(Integer httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    public HandledHttpExceptionDetails(String outcomeMessage, Integer httpStatusCode) {
        super(outcomeMessage);
        this.httpStatusCode = httpStatusCode;
    }
}
