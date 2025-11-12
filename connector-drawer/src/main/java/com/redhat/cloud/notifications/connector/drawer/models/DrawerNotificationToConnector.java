package com.redhat.cloud.notifications.connector.drawer.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.connector.v2.models.NotificationToConnector;
import io.vertx.core.json.JsonObject;
import java.util.Map;
import java.util.Set;

public class DrawerNotificationToConnector extends NotificationToConnector {

    @JsonProperty("drawer_entry_payload")
    DrawerEntryPayload drawerEntryPayload;

    @JsonProperty("recipient_settings")
    Set<RecipientSettings> recipientSettings;

    @JsonProperty("unsubscribers")
    Set<String> unsubscribers;

    @JsonProperty("authorization_criteria")
    JsonObject authorizationCriteria;

    @JsonProperty("event_data")
    Map<String, Object> eventData;

    public DrawerNotificationToConnector(String orgId, DrawerEntryPayload drawerEntryPayload, Set<RecipientSettings> recipientSettings, Set<String> unsubscribers, JsonObject authorizationCriteria, Map<String, Object> eventData) {
        this.setOrgId(orgId);
        this.drawerEntryPayload = drawerEntryPayload;
        this.recipientSettings = recipientSettings;
        this.unsubscribers = unsubscribers;
        this.authorizationCriteria = authorizationCriteria;
        this.eventData = eventData;
    }

    public Set<RecipientSettings> getRecipientSettings() {
        return recipientSettings;
    }

    public void setRecipientSettings(Set<RecipientSettings> recipientSettings) {
        this.recipientSettings = recipientSettings;
    }

    public Set<String> getUnsubscribers() {
        return unsubscribers;
    }

    public void setUnsubscribers(Set<String> unsubscribers) {
        this.unsubscribers = unsubscribers;
    }

    public JsonObject getAuthorizationCriteria() {
        return authorizationCriteria;
    }

    public void setAuthorizationCriteria(JsonObject authorizationCriteria) {
        this.authorizationCriteria = authorizationCriteria;
    }

    public Map<String, Object> getEventData() {
        return eventData;
    }

    public void setEventData(Map<String, Object> eventData) {
        this.eventData = eventData;
    }

    public DrawerEntryPayload getDrawerEntryPayload() {
        return drawerEntryPayload;
    }

    public void setDrawerEntryPayload(DrawerEntryPayload drawerEntryPayload) {
        this.drawerEntryPayload = drawerEntryPayload;
    }
}
