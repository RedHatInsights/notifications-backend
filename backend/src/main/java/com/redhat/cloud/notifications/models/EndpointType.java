package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

// Add new values to the bottom of the enum
// The ordinal order must remain the same as is used internally
// Update test com.redhat.cloud.notifications.models.TestEndpointType to reflect any new enum value
@Schema(enumeration = { "webhook", "email_subscription", "default", "camel" })
public enum EndpointType {
    @JsonProperty("webhook")
    WEBHOOK, // 0
    @JsonProperty("email_subscription")
    EMAIL_SUBSCRIPTION, // 1
    /**
     * This enum member is no longer used.
     * <p>
     * It <b>MUST NOT</b> be deleted because the {@link EndpointType} members ordinal is stored into the database.
     * 
     * @deprecated Since the behavior groups migration.
     */
    @JsonProperty("default")
    @Deprecated
    DEFAULT, // 2
    @JsonProperty("camel")
    CAMEL // 3
}
