package com.redhat.cloud.notifications.connector.drawer;

import com.redhat.cloud.notifications.connector.v2.metrics.NotificationTags;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DrawerMessageHandlerExtractTagsTest {

    private final DrawerMessageHandler handler = new DrawerMessageHandler();

    @Test
    void extractsTagsFromEventDataData() {
        JsonObject inner = new JsonObject()
            .put("bundle", "rhel")
            .put("application", "advisor")
            .put("event_type", "new-recommendation");
        JsonObject cloudEventData = new JsonObject()
            .put("eventData", new JsonObject()
                .put("data", inner)
                .put("environment", new JsonObject()));

        NotificationTags tags = handler.extractNotificationTags(cloudEventData);

        assertEquals("rhel", tags.bundle());
        assertEquals("advisor", tags.application());
        assertEquals("new-recommendation", tags.eventType());
    }

    @Test
    void returnsUnknownWhenEventDataAbsent() {
        JsonObject cloudEventData = new JsonObject().put("org_id", "test-org");

        NotificationTags tags = handler.extractNotificationTags(cloudEventData);

        assertEquals(NotificationTags.UNKNOWN, tags);
    }

    @Test
    void returnsUnknownWhenDataKeyAbsent() {
        JsonObject cloudEventData = new JsonObject()
            .put("eventData", new JsonObject().put("environment", new JsonObject()));

        NotificationTags tags = handler.extractNotificationTags(cloudEventData);

        assertEquals(NotificationTags.UNKNOWN, tags);
    }
}
