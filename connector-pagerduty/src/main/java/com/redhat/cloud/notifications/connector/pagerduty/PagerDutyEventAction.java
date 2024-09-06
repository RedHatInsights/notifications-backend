package com.redhat.cloud.notifications.connector.pagerduty;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum PagerDutyEventAction {
    @JsonProperty("trigger")
    TRIGGER,
    @JsonProperty("acknowledge")
    ACKNOWLEDGE,
    @JsonProperty("resolve")
    RESOLVE
}
