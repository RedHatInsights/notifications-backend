package com.redhat.cloud.notifications.connector.splunk;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.connector.v2.models.NotificationToConnector;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.validation.constraints.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

@RegisterForReflection
public class SplunkNotification extends NotificationToConnector {

    @JsonProperty("account_id")
    public String accountId;

    @NotNull
    @JsonProperty("notif-metadata")
    public JsonObject metadata;

    @NotNull
    public JsonArray events;

    private final Map<String, Object> extraFields = new LinkedHashMap<>();

    @JsonAnySetter
    public void setExtraField(String key, Object value) {
        extraFields.put(key, value);
    }

    public Map<String, Object> getExtraFields() {
        return extraFields;
    }
}
