package com.redhat.cloud.notifications.processors.pagerduty;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(enumeration = { "trigger", "acknowledge", "resolve" })
public enum PagerDutyEventAction {
    @JsonProperty("trigger")
    TRIGGER,
    @JsonProperty("acknowledge")
    ACKNOWLEDGE,
    @JsonProperty("resolve")
    RESOLVE
}
