package com.redhat.cloud.notifications.connector.drawer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.connector.v2.pojo.NotificationToConnector;
import io.vertx.core.json.JsonObject;
import java.util.Collection;
import java.util.Map;

public class DrawerNotificationToConnector extends NotificationToConnector {

    @JsonProperty("drawer_entry_payload")
    DrawerEntryPayload drawerEntryPayload;

    @JsonProperty("recipient_settings")
    Collection<RecipientSettings> recipientSettings;

    @JsonProperty("unsubscribers")
    Collection<String> unsubscribers;

    @JsonProperty("authorization_criteria")
    JsonObject authorizationCriteria;

    @JsonProperty("event_data")
    Map<String, Object> eventData;

    public DrawerNotificationToConnector(String orgId, DrawerEntryPayload drawerEntryPayload, Collection<RecipientSettings> recipientSettings, Collection<String> unsubscribers, JsonObject authorizationCriteria, Map<String, Object> eventData) {
        this.setOrgId(orgId);
        this.drawerEntryPayload = drawerEntryPayload;
        this.recipientSettings = recipientSettings;
        this.unsubscribers = unsubscribers;
        this.authorizationCriteria = authorizationCriteria;
        this.eventData = eventData;
    }

    public Collection<RecipientSettings> getRecipientSettings() {
        return recipientSettings;
    }

    public void setRecipientSettings(Collection<RecipientSettings> recipientSettings) {
        this.recipientSettings = recipientSettings;
    }

    public Collection<String> getUnsubscribers() {
        return unsubscribers;
    }

    public void setUnsubscribers(Collection<String> unsubscribers) {
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
