package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(enumeration = { "ansible", "camel", "drawer", "email_subscription", "webhook", "pagerduty" }) // TODO remove them once the transition to DTOs have been completed.
public enum EndpointType {
    @JsonProperty("webhook") // TODO remove them once the transition to DTOs have been completed.
    WEBHOOK(false, false),
    @JsonProperty("email_subscription") // TODO remove them once the transition to DTOs have been completed.
    EMAIL_SUBSCRIPTION(false, true),
    @JsonProperty("camel") // TODO remove them once the transition to DTOs have been completed.
    CAMEL(true, false),
    @JsonProperty("ansible") // TODO remove them once the transition to DTOs have been completed.
    ANSIBLE(false, false),
    @JsonProperty("drawer") // TODO remove them once the transition to DTOs have been completed.
    DRAWER(false, true),
    @JsonProperty("pagerduty") // TODO remove them once the transition to DTOs have been completed.
    PAGERDUTY(false, false);

    public final boolean requiresSubType;
    public final boolean isSystemEndpointType;

    EndpointType(boolean requiresSubType, boolean isSystemEndpointType) {
        this.requiresSubType = requiresSubType;
        this.isSystemEndpointType = isSystemEndpointType;
    }

    public static boolean isRecipientsEndpointType(final EndpointType endpointType) {
        return DRAWER.equals(endpointType) || EMAIL_SUBSCRIPTION.equals(endpointType);
    }
}
