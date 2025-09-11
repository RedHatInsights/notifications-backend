package com.redhat.cloud.notifications.connector.email.dto;

/**
 * Response DTO for the BOP service.
 */
public class BOPResponse {
    private boolean success;
    private String messageId;
    private String errorMessage;
    private int statusCode;

    public BOPResponse() {
    }

    public BOPResponse(boolean success, String messageId, String errorMessage, int statusCode) {
        this.success = success;
        this.messageId = messageId;
        this.errorMessage = errorMessage;
        this.statusCode = statusCode;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }
}


