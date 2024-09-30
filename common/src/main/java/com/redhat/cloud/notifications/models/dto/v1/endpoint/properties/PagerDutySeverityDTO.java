package com.redhat.cloud.notifications.models.dto.v1.endpoint.properties;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(enumeration = { "critical", "error", "warning", "info" })
public enum PagerDutySeverityDTO {
    @JsonProperty("critical")
    CRITICAL,
    @JsonProperty("error")
    ERROR,
    @JsonProperty("warning")
    WARNING,
    @JsonProperty("info")
    INFO
}

