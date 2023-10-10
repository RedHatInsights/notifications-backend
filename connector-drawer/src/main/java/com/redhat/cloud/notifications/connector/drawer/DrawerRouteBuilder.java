package com.redhat.cloud.notifications.connector.drawer;

import com.redhat.cloud.notifications.connector.ConnectorConfig;
import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.*;
import static org.apache.camel.LoggingLevel.INFO;

@ApplicationScoped
public class DrawerRouteBuilder extends EngineToConnectorRouteBuilder {

    @Inject
    ConnectorConfig drawerConnectorConfig;

    @Override
    public void configureRoute() {
        from(seda(ENGINE_TO_CONNECTOR))
            .routeId(drawerConnectorConfig.getConnectorName())
            .log(INFO, getClass().getName(), "Sent Drawer notification " +
                "[orgId=${exchangeProperty." + ORG_ID + "}, historyId=${exchangeProperty." + ID + "}]")
            .to(direct(SUCCESS));
    }
}

