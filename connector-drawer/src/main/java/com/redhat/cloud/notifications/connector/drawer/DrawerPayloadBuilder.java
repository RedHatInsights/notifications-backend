package com.redhat.cloud.notifications.connector.drawer;

import com.redhat.cloud.notifications.connector.drawer.constant.ExchangeProperty;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerEntry;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerEntryPayload;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerUser;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import java.time.LocalDateTime;
import java.util.List;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static java.time.ZoneOffset.UTC;

@ApplicationScoped
public class DrawerPayloadBuilder implements Processor {

    public static final String CE_SPEC_VERSION = "1.0.2";
    public static final String CE_TYPE = "com.redhat.console.notifications.drawer";

    @Override
    public void process(Exchange exchange) {
        DrawerUser entry = exchange.getMessage().getBody(DrawerUser.class);
        final DrawerEntryPayload entryPayloadModel = exchange.getProperty(ExchangeProperty.DRAWER_ENTRY_PAYLOAD, DrawerEntryPayload.class);
        entryPayloadModel.setId(entry.getDrawerNotificationUuid());

        DrawerEntry drawerEntry = new DrawerEntry();
        drawerEntry.setPayload(entryPayloadModel);
        drawerEntry.setOrganizations(List.of(exchange.getProperty(ORG_ID, String.class)));
        drawerEntry.setUsers(List.of(entry.getId()));
        JsonObject myPayload = JsonObject.mapFrom(drawerEntry);

        Message in = exchange.getIn();

        /*
         * The exchange may contain headers used by a connector to perform a call to an external service.
         * These headers shouldn't be leaked, especially if they contain authorization data, so we're
         * removing all of them for security purposes.
         */
        in.removeHeaders("*");
        in.setHeader("content-type", "application/cloudevents+json; charset=UTF-8");

        JsonObject outgoingCloudEvent = new JsonObject();
        outgoingCloudEvent.put("type", CE_TYPE);
        outgoingCloudEvent.put("specversion", CE_SPEC_VERSION);
        outgoingCloudEvent.put("datacontenttype", "application/json");
        outgoingCloudEvent.put("source", "urn:redhat:source:notifications:drawer");
        outgoingCloudEvent.put("id", entry.getId());
        outgoingCloudEvent.put("time", LocalDateTime.now(UTC).toString());
        outgoingCloudEvent.put("data", myPayload);

        in.setBody(outgoingCloudEvent.encode());
    }
}
