package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonProperty;

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
    DRAWER(false, true);

    public final boolean requiresSubType;
    public final boolean isSystemEndpointType;

    EndpointType(boolean requiresSubType, boolean isSystemEndpointType) {
        this.requiresSubType = requiresSubType;
        this.isSystemEndpointType = isSystemEndpointType;
    }
}
