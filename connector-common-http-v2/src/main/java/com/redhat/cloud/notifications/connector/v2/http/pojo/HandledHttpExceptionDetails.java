package com.redhat.cloud.notifications.connector.v2.http.pojo;

import com.redhat.cloud.notifications.connector.v2.http.HttpErrorType;
import com.redhat.cloud.notifications.connector.v2.pojo.HandledExceptionDetails;

public class HandledHttpExceptionDetails extends HandledExceptionDetails {
    public Integer httpStatusCode;
    public HttpErrorType httpErrorType;
    public String targetUrl;

    public HandledHttpExceptionDetails() {
        this.success = false;
    }

    public HandledHttpExceptionDetails(Integer httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    public HandledHttpExceptionDetails(boolean success, String outcomeMessage, Integer httpStatusCode) {
        super(success, outcomeMessage);
        this.httpStatusCode = httpStatusCode;
    }
}
