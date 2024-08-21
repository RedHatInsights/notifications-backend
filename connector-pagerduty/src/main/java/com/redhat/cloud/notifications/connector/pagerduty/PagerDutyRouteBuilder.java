package com.redhat.cloud.notifications.connector.pagerduty;

import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;
import com.redhat.cloud.notifications.connector.authentication.secrets.SecretsLoader;
import com.redhat.cloud.notifications.connector.http.HttpConnectorConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.URLDecoder;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.LoggingLevel.INFO;

@ApplicationScoped
public class PagerDutyRouteBuilder extends EngineToConnectorRouteBuilder {

    private static final String APPLICATION_JSON = "application/json; charset=utf-8";

    static final String PAGERDUTY_RESPONSE_TIME_METRIC = "micrometer:timer:pagerduty.response.time";
    static final String TIMER_ACTION_START = "?action=start";
    static final String TIMER_ACTION_STOP = "?action=stop";

    @Inject
    HttpConnectorConfig connectorConfig;

    @Inject
    SecretsLoader secretsLoader;

    @Inject
    AuthenticationProcessor authenticationProcessor;

    @Override
    public void configureRoutes() {
        from(seda(ENGINE_TO_CONNECTOR))
            .setHeader(CONTENT_TYPE, constant(APPLICATION_JSON))
            .routeId(connectorConfig.getConnectorName())
            .process(secretsLoader)
            .process(authenticationProcessor)
            .to(PAGERDUTY_RESPONSE_TIME_METRIC + TIMER_ACTION_START)
            .toD(URLDecoder.decode("${exchangeProperty." + TARGET_URL + "}", UTF_8), connectorConfig.getEndpointCacheMaxSize())
            .to(PAGERDUTY_RESPONSE_TIME_METRIC + TIMER_ACTION_STOP)
            .log(INFO, getClass().getName(), "Sent PagerDuty notification " +
                "[orgId=${exchangeProperty." + ORG_ID + "}, historyId=${exchangeProperty." +
                ID + "}, targetUrl=${exchangeProperty." + TARGET_URL + "}]")
            .to(direct(SUCCESS));
    }
}
