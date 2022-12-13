package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(enumeration = { "webhook", "email_subscription", "default", "camel" })
public enum EndpointType {
    @JsonProperty("webhook")
    WEBHOOK(false),
    @JsonProperty("email_subscription")
    EMAIL_SUBSCRIPTION(false),
    /**
     * This enum member is no longer used.
     * @deprecated Since the behavior groups migration.
     */
    @JsonProperty("default")
    @Deprecated
    DEFAULT(false),
    @JsonProperty("camel")
    CAMEL(true);

    public final boolean requiresSubType;

    EndpointType(boolean requiresSubType) {
        this.requiresSubType = requiresSubType;
    }

}
