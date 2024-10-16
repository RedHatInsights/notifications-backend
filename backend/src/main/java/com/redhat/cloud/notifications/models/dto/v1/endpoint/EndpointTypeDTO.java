package com.redhat.cloud.notifications.models.dto.v1.endpoint;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(enumeration = { "ansible", "camel",  "drawer", "email_subscription", "webhook", "pagerduty" })
public enum EndpointTypeDTO {
    @JsonProperty("ansible")
    ANSIBLE(false, false),
    @JsonProperty("camel")
    CAMEL(true, false),
    @JsonProperty("drawer")
    DRAWER(false, true),
    @JsonProperty("email_subscription")
    EMAIL_SUBSCRIPTION(false, true),
    @JsonProperty("webhook")
    WEBHOOK(false, false),
    @JsonProperty("pagerduty")
    PAGERDUTY(false, false);

    public final boolean requiresSubType;
    public final boolean isSystemEndpointType;

    EndpointTypeDTO(boolean requiresSubType, boolean isSystemEndpointType) {
        this.requiresSubType = requiresSubType;
        this.isSystemEndpointType = isSystemEndpointType;
    }
}
