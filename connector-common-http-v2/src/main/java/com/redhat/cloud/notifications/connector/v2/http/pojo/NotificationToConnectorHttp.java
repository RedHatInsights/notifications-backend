package com.redhat.cloud.notifications.connector.v2.http.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.connector.v2.pojo.NotificationToConnector;

public class NotificationToConnectorHttp extends NotificationToConnector {
    @JsonProperty("target_url")
    private String targetUrl;

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }
}
