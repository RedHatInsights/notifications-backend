package com.redhat.cloud.notifications.connector.slack;

import com.redhat.cloud.notifications.connector.ConnectorConfig;
import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static com.redhat.cloud.notifications.connector.slack.ExchangeProperty.CHANNEL;
import static org.apache.camel.LoggingLevel.INFO;

@ApplicationScoped
public class SlackRouteBuilder extends EngineToConnectorRouteBuilder {

    @Inject
    ConnectorConfig connectorConfig;

    @Override
    public void configureRoute() {
        from(direct(ENGINE_TO_CONNECTOR))
                .routeId(connectorConfig.getConnectorName())
                .toD(slack("${exchangeProperty." + CHANNEL + "}").webhookUrl("${exchangeProperty." + TARGET_URL + "}"), connectorConfig.getEndpointCacheMaxSize())
                .log(INFO, getClass().getName(), "Sent Slack notification [orgId=${exchangeProperty." + ORG_ID + "}, historyId=${exchangeProperty." + ID + "}]")
                .to(direct(SUCCESS));
    }
}
