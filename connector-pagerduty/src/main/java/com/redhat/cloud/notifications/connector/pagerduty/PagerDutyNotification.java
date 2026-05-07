package com.redhat.cloud.notifications.connector.pagerduty;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.connector.v2.models.NotificationToConnector;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.vertx.core.json.JsonObject;
import jakarta.validation.constraints.NotNull;

@RegisterForReflection
public class PagerDutyNotification extends NotificationToConnector {

    @JsonProperty("account_id")
    public String accountId;

    @NotNull
    @JsonProperty("payload")
    public JsonObject payload;

    @NotNull
    @JsonProperty("authentication")
    public JsonObject authentication;
}
