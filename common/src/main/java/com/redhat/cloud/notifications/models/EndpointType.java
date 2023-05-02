package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(enumeration = { "webhook", "email_subscription", "camel", "ansible" })
public enum EndpointType {
    @JsonProperty("webhook")
    WEBHOOK(false),
    @JsonProperty("email_subscription")
    EMAIL_SUBSCRIPTION(false),
    @JsonProperty("camel")
    CAMEL(true),
    @JsonProperty("ansible")
    ANSIBLE(false);

    public final boolean requiresSubType;

    EndpointType(boolean requiresSubType) {
        this.requiresSubType = requiresSubType;
    }

}
