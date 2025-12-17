package com.redhat.cloud.notifications.models;

import com.redhat.cloud.notifications.Severity;
import io.vertx.core.json.JsonObject;
import java.util.UUID;


public class EmailAggregation {

    private String orgId;

    private String bundleName;

    private String applicationName;

    private JsonObject payload;

    private Severity severity;

    private UUID eventTypeId;

    public EmailAggregation() {
    }

    public EmailAggregation(String orgId, String bundleName, String applicationName, JsonObject payload, Severity severity, UUID eventTypeId) {
        this.orgId = orgId;
        this.bundleName = bundleName;
        this.applicationName = applicationName;
        this.payload = payload;
        this.severity = severity;
        this.eventTypeId = eventTypeId;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getBundleName() {
        return bundleName;
    }

    public void setBundleName(String bundleName) {
        this.bundleName = bundleName;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public JsonObject getPayload() {
        return payload;
    }

    public void setPayload(JsonObject payload) {
        this.payload = payload;
    }

    public Severity getSeverity() {
        return severity;
    }

    public UUID getEventTypeId() {
        return eventTypeId;
    }

    public void setEventTypeId(UUID eventTypeId) {
        this.eventTypeId = eventTypeId;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }
}
