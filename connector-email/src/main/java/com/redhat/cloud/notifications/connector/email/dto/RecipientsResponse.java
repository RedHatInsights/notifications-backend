package com.redhat.cloud.notifications.connector.email.dto;

import java.util.Set;

/**
 * Response DTO for the recipients resolver service.
 */
public class RecipientsResponse {
    private Set<String> recipients;
    private int totalCount;
    private boolean success;
    private String errorMessage;

    public RecipientsResponse() {
    }

    public RecipientsResponse(Set<String> recipients, int totalCount, boolean success, String errorMessage) {
        this.recipients = recipients;
        this.totalCount = totalCount;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public Set<String> getRecipients() {
        return recipients;
    }

    public void setRecipients(Set<String> recipients) {
        this.recipients = recipients;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}


