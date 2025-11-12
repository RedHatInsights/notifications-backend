package com.redhat.cloud.notifications.connector.drawer;

import com.redhat.cloud.notifications.connector.drawer.models.DrawerEntry;
import com.redhat.cloud.notifications.connector.drawer.models.DrawerEntryPayload;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.reactive.messaging.Message;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Set;


import static java.time.ZoneOffset.UTC;

public class DrawerPayloadBuilder {

    public static final String CE_SPEC_VERSION = "1.0.2";
    public static final String CE_TYPE = "com.redhat.console.notifications.drawer";

    public static Message<JsonObject> buildDrawerMessage(final DrawerEntryPayload entryPayloadModel, final Set<String> recipients) {
        DrawerEntry drawerEntry = new DrawerEntry();
        drawerEntry.setPayload(entryPayloadModel);
        drawerEntry.setUsernames(recipients);
        JsonObject myPayload = JsonObject.mapFrom(drawerEntry);

        OutgoingCloudEventMetadata<JsonObject> cloudEventMetadata = OutgoingCloudEventMetadata.<JsonObject>builder()
            .withId(entryPayloadModel.getEventId().toString())
            .withType(CE_TYPE)
            .withSpecVersion(CE_SPEC_VERSION)
            .withDataContentType("application/json")
            .withSource(URI.create("urn:redhat:source:notifications:drawer"))
            .withTimestamp(ZonedDateTime.now(UTC))
            .build();

        Log.debugf("Built message %s", myPayload);

        return Message.of(myPayload)
            .addMetadata(cloudEventMetadata);
    }
}
