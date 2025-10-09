package com.redhat.cloud.notifications.connector.v2.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NotificationToConnector {
    @JsonProperty("org_id")
    private String orgId;

    @JsonProperty("endpoint_id")
    private String endpointId;

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getEndpointId() {
        return endpointId;
    }

    public void setEndpointId(String endpointId) {
        this.endpointId = endpointId;
    }
}
