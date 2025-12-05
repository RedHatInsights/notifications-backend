package com.redhat.cloud.notifications.connector.drawer;

import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;
import com.redhat.cloud.notifications.connector.drawer.config.DrawerConnectorConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;

@ApplicationScoped
public class DrawerRouteBuilder extends EngineToConnectorRouteBuilder {

    @Inject
    DrawerConnectorConfig drawerConnectorConfig;

    @Inject
    DrawerProcessor drawerProcessor;

    public static final String CONNECTOR_TO_DRAWER = "connector-to-drawer";

    @Override
    public void configureRoutes() {
        from(seda(ENGINE_TO_CONNECTOR))
            .routeId(drawerConnectorConfig.getConnectorName())
            .process(drawerProcessor)
            .to(direct(SUCCESS));
    }
}
