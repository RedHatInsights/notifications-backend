package com.redhat.cloud.notifications.connector.v2.http.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.connector.v2.pojo.NotificationToConnector;
import io.vertx.core.json.JsonObject;

public class NotificationToConnectorHttp extends NotificationToConnector {

    @JsonProperty("endpoint_properties")
    private EndpointProperties endpointProperties;

    @JsonProperty("payload")
    private JsonObject payload;

    public static class EndpointProperties {
        @JsonProperty("url")
        private String targetUrl;

        public String getTargetUrl() {
            return targetUrl;
        }

        public void setTargetUrl(String targetUrl) {
            this.targetUrl = targetUrl;
        }
    }

    public EndpointProperties getEndpointProperties() {
        return endpointProperties;
    }

    public void setEndpointProperties(EndpointProperties endpointProperties) {
        this.endpointProperties = endpointProperties;
    }

    public JsonObject getPayload() {
        return payload;
    }

    public void setPayload(JsonObject payload) {
        this.payload = payload;
    }
}
