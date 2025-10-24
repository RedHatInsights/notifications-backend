package com.redhat.cloud.notifications.connector.v2;

import com.redhat.cloud.notifications.connector.v2.pojo.HandledMessageDetails;
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
        return new HandledMessageDetails(true, "Ok");
    }
}
