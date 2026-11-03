package com.redhat.cloud.notifications.connector.v2.metrics;

import io.vertx.core.json.JsonObject;

public record NotificationTags(String bundle, String application, String eventType) {

    public static final NotificationTags UNKNOWN = new NotificationTags("unknown", "unknown", "unknown");

    public static NotificationTags fromJsonObject(JsonObject data) {
        return new NotificationTags(
            stringOrUnknown(data.getString("bundle")),
            stringOrUnknown(data.getString("application")),
            stringOrUnknown(data.getString("event_type"))
        );
    }

    private static String stringOrUnknown(String value) {
        return (value != null && !value.isEmpty()) ? value : "unknown";
    }
}
