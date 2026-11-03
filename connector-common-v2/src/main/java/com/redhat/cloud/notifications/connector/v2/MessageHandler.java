package com.redhat.cloud.notifications.connector.v2;

import com.redhat.cloud.notifications.connector.v2.metrics.NotificationTags;
import com.redhat.cloud.notifications.connector.v2.models.HandledMessageDetails;
import io.quarkus.arc.DefaultBean;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

import static com.redhat.cloud.notifications.connector.v2.ConnectorConfig.BASE_CONFIG_PRIORITY;

@ApplicationScoped
@DefaultBean
@Priority(BASE_CONFIG_PRIORITY)
public class MessageHandler {

    public HandledMessageDetails handle(IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {
        Log.info("Test implementation of MessageHandler for unit testing");
        return new HandledMessageDetails("Ok");
    }

    // All production connectors override this. The default is a last-resort fallback
    // for tests or future connectors that haven't added an override yet.
    public NotificationTags extractNotificationTags(JsonObject cloudEventData) {
        return NotificationTags.UNKNOWN;
    }
}
