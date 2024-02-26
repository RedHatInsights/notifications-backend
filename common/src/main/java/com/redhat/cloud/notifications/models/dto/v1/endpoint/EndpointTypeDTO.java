package com.redhat.cloud.notifications.models.dto.v1.endpoint;

import com.fasterxml.jackson.annotation.JsonValue;

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

    @JsonValue
    public String toLowerCase() {
        return this.toString().toLowerCase();
    }
}
