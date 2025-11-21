package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(enumeration = { "ansible", "camel", "drawer", "email_subscription", "webhook", "pagerduty" }) // TODO remove them once the transition to DTOs have been completed.
public enum EndpointType {
    @JsonProperty("webhook") // TODO remove them once the transition to DTOs have been completed.
    WEBHOOK(false),
    @JsonProperty("email_subscription") // TODO remove them once the transition to DTOs have been completed.
    EMAIL_SUBSCRIPTION(false),
    @JsonProperty("camel") // TODO remove them once the transition to DTOs have been completed.
    CAMEL(true),
    @JsonProperty("ansible") // TODO remove them once the transition to DTOs have been completed.
    ANSIBLE(false),
    @JsonProperty("drawer") // TODO remove them once the transition to DTOs have been completed.
    DRAWER(false),
    @JsonProperty("pagerduty") // TODO remove them once the transition to DTOs have been completed.
    PAGERDUTY(false);

    public final boolean requiresSubType;

    EndpointType(boolean requiresSubType) {
        this.requiresSubType = requiresSubType;
    }

    public static boolean isRecipientsEndpointType(final EndpointType endpointType) {
        return DRAWER.equals(endpointType) || EMAIL_SUBSCRIPTION.equals(endpointType);
    }
}
