package com.redhat.cloud.notifications.connector.http;

import com.redhat.cloud.notifications.connector.CamelComponentConfigurator;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.component.http.HttpComponent;

@Dependent
public class HttpComponentConfigurator implements CamelComponentConfigurator {

    @Inject
    HttpConnectorConfig connectorConfig;

    @Override
    public void configure(CamelContext context) {
        for (String httpComponent : connectorConfig.getHttpComponents()) {
            HttpComponent component = context.getComponent(httpComponent, HttpComponent.class);
            component.setConnectTimeout(connectorConfig.getHttpConnectTimeout());
            component.setConnectionsPerRoute(connectorConfig.getHttpConnectionsPerRoute());
            component.setMaxTotalConnections(connectorConfig.getHttpMaxTotalConnections());
            component.setSoTimeout(connectorConfig.getHttpSocketTimeout());
            Log.debugf("Configured %s component", httpComponent);
        }
    }
}
