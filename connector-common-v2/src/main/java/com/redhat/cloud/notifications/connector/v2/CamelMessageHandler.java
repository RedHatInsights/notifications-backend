package com.redhat.cloud.notifications.connector.v2;

import com.redhat.cloud.notifications.connector.v2.metrics.NotificationTags;
import io.vertx.core.json.JsonObject;

public abstract class CamelMessageHandler extends MessageHandler {

    @Override
    public NotificationTags extractNotificationTags(JsonObject cloudEventData) {
        JsonObject eventData = cloudEventData.getJsonObject("eventData");
        return eventData != null ? NotificationTags.fromJsonObject(eventData) : NotificationTags.UNKNOWN;
    }
}
