package com.redhat.cloud.notifications.connector.v2;

import io.quarkus.arc.DefaultBean;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.net.URI;
import java.time.ZonedDateTime;

import static com.redhat.cloud.notifications.connector.v2.CommonConstants.ENDPOINT_ID;
import static com.redhat.cloud.notifications.connector.v2.CommonConstants.ORG_ID;
import static com.redhat.cloud.notifications.connector.v2.CommonConstants.OUTCOME;
import static com.redhat.cloud.notifications.connector.v2.CommonConstants.RETURN_SOURCE;
import static com.redhat.cloud.notifications.connector.v2.CommonConstants.START_TIME;
import static com.redhat.cloud.notifications.connector.v2.CommonConstants.SUCCESSFUL;
import static com.redhat.cloud.notifications.connector.v2.CommonConstants.TARGET_URL;
import static java.time.ZoneOffset.UTC;

@DefaultBean
@ApplicationScoped
public class OutgoingCloudEventBuilder {

    public static final String CE_SPEC_VERSION = "1.0";
    public static final String CE_TYPE = "com.redhat.console.notifications.history";

    public Message<String> build(MessageContext context) throws Exception {

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

        OutgoingCloudEventMetadata<String> cloudEventMetadata = OutgoingCloudEventMetadata.<String>builder()
            .withId(context.getIncomingCloudEventMetadata().getId())
            .withType(CE_TYPE)
            .withSpecVersion(CE_SPEC_VERSION)
            .withSource(URI.create(context.getProperty(RETURN_SOURCE, String.class)))
            .withTimestamp(ZonedDateTime.now(UTC))
            .withDataContentType("application/json")
            .build();

        return Message.of(data.encode()).addMetadata(cloudEventMetadata);
    }
}
