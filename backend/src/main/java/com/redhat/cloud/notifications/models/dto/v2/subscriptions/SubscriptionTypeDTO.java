package com.redhat.cloud.notifications.models.dto.v2.subscriptions;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(enumeration = { "instant_email", "daily_email", "drawer" })
public enum SubscriptionTypeDTO {
    @JsonProperty("instant_email")
    INSTANT,
    @JsonProperty("daily_email")
    DAILY,
    @JsonProperty("drawer")
    DRAWER
}
