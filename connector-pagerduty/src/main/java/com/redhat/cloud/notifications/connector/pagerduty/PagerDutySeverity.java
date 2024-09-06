package com.redhat.cloud.notifications.connector.pagerduty;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum PagerDutySeverity {
    @JsonProperty("critical")
    CRITICAL,
    @JsonProperty("error")
    ERROR,
    @JsonProperty("warning")
    WARNING,
    @JsonProperty("info")
    INFO
}
