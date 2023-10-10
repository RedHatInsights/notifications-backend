package com.redhat.cloud.notifications.connector.drawer;

import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import com.redhat.cloud.notifications.connector.drawer.constant.ExchangeProperty;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerNotificationToConnector;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;

@ApplicationScoped
public class DrawerCloudEventDataExtractor extends CloudEventDataExtractor {

    @Override
    public void extract(Exchange exchange, JsonObject cloudEventData) {
        DrawerNotificationToConnector connector =  cloudEventData.mapTo(DrawerNotificationToConnector.class);
        Log.info(connector);
        exchange.setProperty(ExchangeProperty.DRAWER_ENTRY_PAYLOAD_MODEL, connector.drawerEntryPayload());
        exchange.setProperty(ExchangeProperty.RECIPIENT_SETTINGS, connector.recipientSettings());
        exchange.setProperty(ExchangeProperty.UNSUBSCRIBERS, connector.unsubscribers());
    }
}
