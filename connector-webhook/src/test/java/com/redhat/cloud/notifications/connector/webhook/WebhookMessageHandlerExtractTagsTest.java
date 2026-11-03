package com.redhat.cloud.notifications.connector.webhook;

import com.redhat.cloud.notifications.connector.v2.metrics.NotificationTags;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebhookMessageHandlerExtractTagsTest {

    private final WebhookMessageHandler handler = new WebhookMessageHandler();

    @Test
    void extractsTagsFromPayload() {
        JsonObject payload = new JsonObject()
            .put("bundle", "rhel")
            .put("application", "advisor")
            .put("event_type", "new-recommendation");
        JsonObject cloudEventData = new JsonObject()
            .put("endpoint_properties", new JsonObject().put("url", "https://example.com"))
            .put("payload", payload);

        NotificationTags tags = handler.extractNotificationTags(cloudEventData);

        assertEquals("rhel", tags.bundle());
        assertEquals("advisor", tags.application());
        assertEquals("new-recommendation", tags.eventType());
    }

    @Test
    void returnsUnknownWhenPayloadAbsent() {
        JsonObject cloudEventData = new JsonObject()
            .put("endpoint_properties", new JsonObject().put("url", "https://example.com"));

        NotificationTags tags = handler.extractNotificationTags(cloudEventData);

        assertEquals(NotificationTags.UNKNOWN, tags);
    }
}
