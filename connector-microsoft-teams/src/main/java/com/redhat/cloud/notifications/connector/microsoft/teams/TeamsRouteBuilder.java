package com.redhat.cloud.notifications.connector.microsoft.teams;

import com.redhat.cloud.notifications.connector.ConnectorConfig;
import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.apache.camel.LoggingLevel.INFO;

@ApplicationScoped
public class TeamsRouteBuilder extends EngineToConnectorRouteBuilder {

    @Inject
    ConnectorConfig connectorConfig;

    @Override
    public void configureRoute() {
        from(direct(ENGINE_TO_CONNECTOR))
                .routeId(connectorConfig.getConnectorName())
                .setHeader(HTTP_METHOD, constant("POST"))
                .setHeader(CONTENT_TYPE, constant("application/json"))
                .toD("${exchangeProperty." + TARGET_URL + "}", connectorConfig.getEndpointCacheMaxSize())
                .log(INFO, getClass().getName(), "Sent Microsoft Teams notification [orgId=${exchangeProperty." + ORG_ID + "}, historyId=${exchangeProperty." + ID + "}]")
                .to(direct(SUCCESS));
    }
}
