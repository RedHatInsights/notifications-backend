package com.redhat.cloud.notifications.connector.drawer;

import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;
import com.redhat.cloud.notifications.connector.drawer.config.DrawerConnectorConfig;
import com.redhat.cloud.notifications.connector.drawer.constant.ExchangeProperty;
import com.redhat.cloud.notifications.connector.drawer.recipients.RecipientsResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.*;
import static org.apache.camel.LoggingLevel.INFO;

@ApplicationScoped
public class DrawerRouteBuilder extends EngineToConnectorRouteBuilder {

    @Inject
    DrawerConnectorConfig drawerConnectorConfig;

    @Inject
    RecipientsResolver recipientsResolver;

    @Inject
    DrawerPayloadBuilder drawerPayloadBuilder;

    public static final String CONNECTOR_TO_DRAWER = "connector-to-drawer";

    @Override
    public void configureRoute() {
        from(seda(ENGINE_TO_CONNECTOR))
            .routeId(drawerConnectorConfig.getConnectorName())
            .process(recipientsResolver)
            .split(simpleF("${exchangeProperty.%s}", ExchangeProperty.RESOLVED_RECIPIENT_LIST))
                .process(drawerPayloadBuilder)
                .to(log(getClass().getName()).level("INFO").showHeaders(true).showBody(true))
                .to(direct(CONNECTOR_TO_DRAWER))
            .end()
            .log(INFO, getClass().getName(), "Sent Drawer notification " +
                "[orgId=${exchangeProperty." + ORG_ID + "}, historyId=${exchangeProperty." + ID + "}]")
            .to(direct(SUCCESS));

        from(direct(CONNECTOR_TO_DRAWER))
            .routeId(CONNECTOR_TO_DRAWER)
            .to(kafka(drawerConnectorConfig.getOutgoingDrawerTopic()));
    }
}
