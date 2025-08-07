package com.redhat.cloud.notifications.connector.drawer;

import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import com.redhat.cloud.notifications.connector.drawer.config.DrawerConnectorConfig;
import com.redhat.cloud.notifications.connector.drawer.constant.ExchangeProperty;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerNotificationToConnector;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;

@ApplicationScoped
public class DrawerCloudEventDataExtractor extends CloudEventDataExtractor {

    @Inject
    DrawerConnectorConfig drawerConnectorConfig;

    @Override
    public void extract(Exchange exchange, JsonObject cloudEventData) {
        DrawerNotificationToConnector notification =  cloudEventData.mapTo(DrawerNotificationToConnector.class);
        exchange.setProperty(ExchangeProperty.DRAWER_ENTRY_PAYLOAD, notification.drawerEntryPayload());
        exchange.setProperty(ExchangeProperty.RECIPIENT_SETTINGS, notification.recipientSettings());
        exchange.setProperty(ExchangeProperty.UNSUBSCRIBERS, notification.unsubscribers());
        exchange.setProperty(ExchangeProperty.UNSUBSCRIBERS, notification.unsubscribers());
        exchange.setProperty(ExchangeProperty.AUTHORIZATION_CRITERIA, notification.authorizationCriteria());
        exchange.setProperty(ExchangeProperty.USE_SIMPLIFIED_ROUTE, drawerConnectorConfig.useSimplifiedRoute(notification.orgId()));
        exchange.setProperty(ExchangeProperty.EVENT_DATA, notification.eventData());
    }
}
