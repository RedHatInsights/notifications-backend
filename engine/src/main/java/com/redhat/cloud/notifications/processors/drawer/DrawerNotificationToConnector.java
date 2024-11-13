package com.redhat.cloud.notifications.processors.drawer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.models.DrawerEntryPayload;
import com.redhat.cloud.notifications.processors.ExternalAuthorizationCriterion;
import com.redhat.cloud.notifications.processors.email.connector.dto.RecipientSettings;
import java.util.Collection;

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
    ExternalAuthorizationCriterion externalAuthorizationCriteria
)  { }
