package com.redhat.cloud.notifications.models.dto.v2.subscriptions;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(enumeration = { "critical", "important", "moderate", "low", "none" })
public enum SeverityDTO {
    @JsonProperty("critical")
    CRITICAL,
    @JsonProperty("important")
    IMPORTANT,
    @JsonProperty("moderate")
    MODERATE,
    @JsonProperty("low")
    LOW,
    @JsonProperty("none")
    NONE
}
