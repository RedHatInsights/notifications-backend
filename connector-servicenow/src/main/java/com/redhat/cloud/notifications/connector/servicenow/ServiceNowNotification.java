package com.redhat.cloud.notifications.connector.servicenow;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.connector.v2.models.NotificationToConnector;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.vertx.core.json.JsonObject;
import jakarta.validation.constraints.NotNull;

@RegisterForReflection
public class ServiceNowNotification extends NotificationToConnector {

    static final String URL_KEY = "url";

    @JsonProperty("account_id")
    public String accountId;

    @NotNull
    @JsonProperty("notif-metadata")
    public JsonObject metadata;

    public String getTargetUrl() {
        return metadata.getString(URL_KEY);
    }
}
