package com.redhat.cloud.notifications;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Recognized levels for {@link com.redhat.cloud.notifications.ingress.Action#severity}
 */
public enum Severity {
    @JsonProperty("Critical")
    CRITICAL,
    @JsonProperty("Important")
    IMPORTANT,
    @JsonProperty("Moderate")
    MODERATE,
    @JsonProperty("Low")
    LOW,
    @JsonProperty("None")
    NONE,
    @JsonProperty("Undefined")
    UNDEFINED
}
