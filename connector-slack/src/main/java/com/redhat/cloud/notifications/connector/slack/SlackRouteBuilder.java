package com.redhat.cloud.notifications.connector.slack;

import com.redhat.cloud.notifications.connector.ConnectorConfig;
import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static com.redhat.cloud.notifications.connector.slack.ExchangeProperty.CHANNEL;
import static org.apache.camel.LoggingLevel.INFO;

@ApplicationScoped
public class SlackRouteBuilder extends EngineToConnectorRouteBuilder {

    static final String SLACK_RESPONSE_TIME_METRIC = "micrometer:timer:slack.response.time";
    static final String TIMER_ACTION_START = "?action=start";
    static final String TIMER_ACTION_STOP = "?action=stop";

    @Inject
    ConnectorConfig connectorConfig;

    @Override
    public void configureRoutes() {
        from(seda(ENGINE_TO_CONNECTOR))
                .routeId(connectorConfig.getConnectorName())
                .to(SLACK_RESPONSE_TIME_METRIC + TIMER_ACTION_START)
                    .toD(slack("${exchangeProperty." + CHANNEL + "}").webhookUrl("${exchangeProperty." + TARGET_URL + "}"), connectorConfig.getEndpointCacheMaxSize())
                .to(SLACK_RESPONSE_TIME_METRIC + TIMER_ACTION_STOP)
                .log(INFO, getClass().getName(), "Sent Slack notification [orgId=${exchangeProperty." + ORG_ID + "}, historyId=${exchangeProperty." + ID + "}]")
                .to(direct(SUCCESS));
    }
}
