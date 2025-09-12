package com.redhat.cloud.notifications.connector.slack.dto;

import io.vertx.core.json.JsonObject;

/**
 * Request DTO for Slack message delivery.
 */
public class SlackRequest {
    private String targetUrl;
    private JsonObject payload;
    private String contentType;

    public SlackRequest() {
    }

    public SlackRequest(String targetUrl, JsonObject payload, String contentType) {
        this.targetUrl = targetUrl;
        this.payload = payload;
        this.contentType = contentType;
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

    public static class Builder {
        private String targetUrl;
        private JsonObject payload;
        private String contentType;

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

        public SlackRequest build() {
            return new SlackRequest(targetUrl, payload, contentType);
        }
    }
}


