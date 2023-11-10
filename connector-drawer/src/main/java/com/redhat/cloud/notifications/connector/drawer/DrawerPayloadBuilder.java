package com.redhat.cloud.notifications.connector.drawer;

import com.redhat.cloud.notifications.connector.drawer.constant.ExchangeProperty;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerEntry;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerEntryPayload;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerUser;
import io.vertx.core.json.Json;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.util.json.JsonObject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.OUTCOME;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.START_TIME;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.SUCCESSFUL;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TYPE;
import static java.time.ZoneOffset.UTC;

@ApplicationScoped
public class DrawerPayloadBuilder implements Processor {

    public static final String CE_SPEC_VERSION = "1.0.2";
    public static final String CE_TYPE = "com.redhat.console.notifications.drawer";

    @Override
    public void process(Exchange exchange) {
        DrawerUser entry = exchange.getMessage().getBody(DrawerUser.class);
        final DrawerEntryPayload entryPayloadModel = exchange.getProperty(ExchangeProperty.DRAWER_ENTRY_PAYLOAD, DrawerEntryPayload.class);
        DrawerEntryPayload payloadToSend = new DrawerEntryPayload();
        payloadToSend.setDescription(entryPayloadModel.getDescription());
        payloadToSend.setCreated(entryPayloadModel.getCreated());
        payloadToSend.setRead(entryPayloadModel.isRead());
        payloadToSend.setSource(entryPayloadModel.getSource());
        payloadToSend.setTitle(entryPayloadModel.getTitle());

        payloadToSend.setId(entry.getDrawerNotificationUuid());

        DrawerEntry drawerEntry = new DrawerEntry();
        drawerEntry.setPayload(payloadToSend);
        drawerEntry.setOrganizations(List.of(exchange.getProperty(ORG_ID, String.class)));
        drawerEntry.setUsers(List.of(entry.getId()));
        Map<String, Object> map = Json.CODEC.fromValue(drawerEntry, Map.class);
        JsonObject myPayload = new JsonObject(map);

        Message in = exchange.getIn();

        /*
         * The exchange may contain headers used by a connector to perform a call to an external service.
         * These headers shouldn't be leaked, especially if they contain authorization data, so we're
         * removing all of them for security purposes.
         */
        in.removeHeaders("*");
        in.setHeader("content-type", "application/cloudevents+json; charset=UTF-8");

        JsonObject details = new JsonObject();
        details.put("type", exchange.getProperty(TYPE, String.class));
        details.put("target", exchange.getProperty(TARGET_URL, String.class));
        details.put("outcome", exchange.getProperty(OUTCOME, String.class));

        JsonObject data = new JsonObject();
        data.put("successful", exchange.getProperty(SUCCESSFUL, Boolean.class));
        data.put("duration", System.currentTimeMillis() - exchange.getProperty(START_TIME, Long.class));
        data.put("details", details);

        JsonObject outgoingCloudEvent = new JsonObject();
        outgoingCloudEvent.put("type", CE_TYPE);
        outgoingCloudEvent.put("specversion", CE_SPEC_VERSION);
        outgoingCloudEvent.put("datacontenttype", "application/json");
        outgoingCloudEvent.put("source", "urn:redhat:source:notifications:drawer");
        outgoingCloudEvent.put("id", entry.getId());
        outgoingCloudEvent.put("time", LocalDateTime.now(UTC).toString());
        outgoingCloudEvent.put("data", myPayload);

        in.setBody(outgoingCloudEvent.toJson());
    }
}
