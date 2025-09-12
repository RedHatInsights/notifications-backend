package com.redhat.cloud.notifications.connector.webhook.dto;

/**
 * Response DTO for webhook delivery.
 */
public class WebhookResponse {
    private boolean success;
    private int statusCode;
    private String errorMessage;
    private String responseBody;

    public WebhookResponse() {
    }

    public WebhookResponse(boolean success, int statusCode, String errorMessage, String responseBody) {
        this.success = success;
        this.statusCode = statusCode;
        this.errorMessage = errorMessage;
        this.responseBody = responseBody;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }
}


