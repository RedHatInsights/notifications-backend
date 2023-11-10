package com.redhat.cloud.notifications.connector.drawer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collection;

public record DrawerNotificationToConnector(

    @JsonProperty("orgId")
    String orgId,

    @JsonProperty("drawer_entry_payload")
    DrawerEntryPayload drawerEntryPayload,

    @JsonProperty("recipient_settings")
    Collection<RecipientSettings> recipientSettings,

    @JsonProperty("unsubscribers")
    Collection<String> unsubscribers
)  { }
