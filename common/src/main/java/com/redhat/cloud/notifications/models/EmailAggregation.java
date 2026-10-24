package com.redhat.cloud.notifications.models;

import com.redhat.cloud.notifications.Severity;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.UUID;


public class EmailAggregation {

    private String orgId;

    private String bundleName;

    private String applicationName;

    private JsonObject payload;

    private Severity severity;

    private UUID eventTypeId;

    /**
     * The event type's name, needed because {@code SubscriptionRepository.getSubscribersByEventType} is
     * keyed by name, not ID. Additive field, set via setter only -- no constructor change, so existing
     * callers (engine's per-recipient {@code EmailAggregator}) are unaffected.
     */
    private String eventTypeName;

    /**
     * Whether this event's type is subscribed-to by default. Read straight off
     * {@code EventType.isSubscribedByDefault()}. Additive, see {@link #eventTypeName}.
     */
    private boolean subscribedByDefault;

    /**
     * The raw {@code recipients} override array copied verbatim from the originating event's payload,
     * when present, or {@code null} otherwise. Not parsed or resolved here -- just forwarded. Additive,
     * see {@link #eventTypeName}.
     */
    private JsonArray recipientsOverride;

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

    public String getEventTypeName() {
        return eventTypeName;
    }

    public void setEventTypeName(String eventTypeName) {
        this.eventTypeName = eventTypeName;
    }

    public boolean isSubscribedByDefault() {
        return subscribedByDefault;
    }

    public void setSubscribedByDefault(boolean subscribedByDefault) {
        this.subscribedByDefault = subscribedByDefault;
    }

    public JsonArray getRecipientsOverride() {
        return recipientsOverride;
    }

    public void setRecipientsOverride(JsonArray recipientsOverride) {
        this.recipientsOverride = recipientsOverride;
    }
}
