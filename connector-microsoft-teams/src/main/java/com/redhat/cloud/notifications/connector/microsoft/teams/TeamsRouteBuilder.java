package com.redhat.cloud.notifications.connector.microsoft.teams;

import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;
import com.redhat.cloud.notifications.connector.http.HttpConnectorConfig;
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

    static final String TEAMS_RESPONSE_TIME_METRIC = "micrometer:timer:teams.response.time";
    static final String TIMER_ACTION_START = "?action=start";
    static final String TIMER_ACTION_STOP = "?action=stop";

    @Inject
    HttpConnectorConfig connectorConfig;

    @Override
    public void configureRoutes() {
        from(seda(ENGINE_TO_CONNECTOR))
                .routeId(connectorConfig.getConnectorName())
                .setHeader(HTTP_METHOD, constant("POST"))
                .setHeader(CONTENT_TYPE, constant("application/json"))
                .to(TEAMS_RESPONSE_TIME_METRIC + TIMER_ACTION_START)
                    .toD("${exchangeProperty." + TARGET_URL + "}", connectorConfig.getEndpointCacheMaxSize())
                .to(TEAMS_RESPONSE_TIME_METRIC + TIMER_ACTION_STOP)
                .log(INFO, getClass().getName(), "Sent Microsoft Teams notification [orgId=${exchangeProperty." + ORG_ID + "}, historyId=${exchangeProperty." + ID + "}]")
                .to(direct(SUCCESS));
    }
}
