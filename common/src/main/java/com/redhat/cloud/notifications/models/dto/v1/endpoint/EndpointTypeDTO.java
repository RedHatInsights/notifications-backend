package com.redhat.cloud.notifications.models.dto.v1.endpoint;

import com.fasterxml.jackson.annotation.JsonValue;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(enumeration = { "webhook", "email_subscription", "camel", "ansible", "drawer" })
public enum EndpointTypeDTO {
    ANSIBLE(false, false),
    CAMEL(true, false),
    DRAWER(false, true),
    EMAIL_SUBSCRIPTION(false, true),
    WEBHOOK(false, false);

    public final boolean requiresSubType;
    public final boolean isSystemEndpointType;

    EndpointTypeDTO(boolean requiresSubType, boolean isSystemEndpointType) {
        this.requiresSubType = requiresSubType;
        this.isSystemEndpointType = isSystemEndpointType;
    }

    /**
     * Transforms the enum values to lowercase.
     * @return the enum value in lowercase.
     */
    @JsonValue
    public String toLowerCase() {
        return this.toString().toLowerCase();
    }
}
