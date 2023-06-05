package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(enumeration = { "webhook", "email_subscription", "camel", "ansible", "drawer" })
public enum EndpointType {
    @JsonProperty("webhook")
    WEBHOOK(false, false),
    @JsonProperty("email_subscription")
    EMAIL_SUBSCRIPTION(false, true),
    @JsonProperty("camel")
    CAMEL(true, false),
    @JsonProperty("ansible")
    ANSIBLE(false, false),
    @JsonProperty("drawer")
    DRAWER(false, true);

    public final boolean requiresSubType;
    public final boolean isSystemEndpointType;

    EndpointType(boolean requiresSubType, boolean isSystemEndpointType) {
        this.requiresSubType = requiresSubType;
        this.isSystemEndpointType = isSystemEndpointType;
    }

}
