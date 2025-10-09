package com.redhat.cloud.notifications.connector.v2;

import io.quarkus.arc.DefaultBean;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;

import static com.redhat.cloud.notifications.connector.v2.ExchangeProperty.ENDPOINT_ID;
import static com.redhat.cloud.notifications.connector.v2.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.v2.ExchangeProperty.OUTCOME;
import static com.redhat.cloud.notifications.connector.v2.ExchangeProperty.RETURN_SOURCE;
import static com.redhat.cloud.notifications.connector.v2.ExchangeProperty.START_TIME;
import static com.redhat.cloud.notifications.connector.v2.ExchangeProperty.SUCCESSFUL;
import static com.redhat.cloud.notifications.connector.v2.ExchangeProperty.TARGET_URL;
import static java.time.ZoneOffset.UTC;

@DefaultBean
@ApplicationScoped
public class OutgoingCloudEventBuilder {

    public static final String CE_SPEC_VERSION = "1.0";
    public static final String CE_TYPE = "com.redhat.console.notifications.history";

    public JsonObject build(MessageContext context) throws Exception {

        JsonObject details = new JsonObject();
        details.put("type", context.getIncomingCloudEventMetadata().getType());
        details.put("target", context.getProperty(TARGET_URL, String.class));
        details.put("outcome", context.getProperty(OUTCOME, String.class));

        JsonObject data = new JsonObject();
        data.put("successful", context.getProperty(SUCCESSFUL, Boolean.class));
        data.put("duration", System.currentTimeMillis() - context.getProperty(START_TIME, Long.class));
        data.put("details", details);

        Log.infof("Notification sent [orgId=%s, EndpointId=%s, type=%s, historyId=%s, duration=%d, successful=%b]",
            context.getProperty(ORG_ID, String.class),
            context.getProperty(ENDPOINT_ID, String.class),
            context.getProperty(RETURN_SOURCE, String.class),
            context.getIncomingCloudEventMetadata().getId(),
            data.getLong("duration"),
            context.getProperty(SUCCESSFUL, Boolean.class));

        JsonObject outgoingCloudEvent = new JsonObject();
        outgoingCloudEvent.put("type", CE_TYPE);
        outgoingCloudEvent.put("specversion", CE_SPEC_VERSION);
        outgoingCloudEvent.put("source", context.getProperty(RETURN_SOURCE, String.class));
        outgoingCloudEvent.put("id", context.getIncomingCloudEventMetadata().getId());
        outgoingCloudEvent.put("time", LocalDateTime.now(UTC).toString());
        outgoingCloudEvent.put("data", data.encode());

        return outgoingCloudEvent;
    }
}
