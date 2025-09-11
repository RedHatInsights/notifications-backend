package com.redhat.cloud.notifications.connector.webhook.dto;

import io.vertx.core.json.JsonObject;

/**
 * Request DTO for webhook delivery.
 */
public class WebhookRequest {
    private String targetUrl;
    private JsonObject payload;
    private String contentType;
    private boolean trustAll;

    public WebhookRequest() {
    }

    public WebhookRequest(String targetUrl, JsonObject payload, String contentType, boolean trustAll) {
        this.targetUrl = targetUrl;
        this.payload = payload;
        this.contentType = contentType;
        this.trustAll = trustAll;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public JsonObject getPayload() {
        return payload;
    }

    public void setPayload(JsonObject payload) {
        this.payload = payload;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public boolean isTrustAll() {
        return trustAll;
    }

    public void setTrustAll(boolean trustAll) {
        this.trustAll = trustAll;
    }

    public static class Builder {
        private String targetUrl;
        private JsonObject payload;
        private String contentType;
        private boolean trustAll;

        public Builder targetUrl(String targetUrl) {
            this.targetUrl = targetUrl;
            return this;
        }

        public Builder payload(JsonObject payload) {
            this.payload = payload;
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder trustAll(boolean trustAll) {
            this.trustAll = trustAll;
            return this;
        }

        public WebhookRequest build() {
            return new WebhookRequest(targetUrl, payload, contentType, trustAll);
        }
    }
}


