package com.redhat.cloud.notifications.connector.drawer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.json.JsonObject;
import java.util.Collection;
import java.util.Map;

public record DrawerNotificationToConnector(

    @JsonProperty("orgId")
    String orgId,

    @JsonProperty("drawer_entry_payload")
    DrawerEntryPayload drawerEntryPayload,

    @JsonProperty("recipient_settings")
    Collection<RecipientSettings> recipientSettings,

    @JsonProperty("unsubscribers")
    Collection<String> unsubscribers,

    @JsonProperty("authorization_criteria")
    JsonObject authorizationCriteria,

    @JsonProperty("event_data")
    Map<String, Object> eventData
)  { }
