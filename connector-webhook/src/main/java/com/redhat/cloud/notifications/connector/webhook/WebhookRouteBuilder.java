package com.redhat.cloud.notifications.connector.webhook;

import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;
import com.redhat.cloud.notifications.connector.authentication.secrets.SecretsLoader;
import com.redhat.cloud.notifications.connector.http.HttpConnectorConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.endpoint.dsl.HttpEndpointBuilderFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TYPE;
import static com.redhat.cloud.notifications.connector.http.SslTrustAllManager.getSslContextParameters;
import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.TARGET_URL_NO_SCHEME;
import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.TRUST_ALL;
import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.LoggingLevel.INFO;

@ApplicationScoped
public class WebhookRouteBuilder extends EngineToConnectorRouteBuilder {

    public static final String CLOUD_EVENT_TYPE_PREFIX = "com.redhat.console.notification.toCamel.";
    private static final String APPLICATION_JSON = "application/json; charset=utf-8";

    static final String ENDPOINT_RESPONSE_TIME_METRIC = "micrometer:timer:webhook.endpoint.response.time";
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
            // SSL certificates may or may not be verified depending on the integration settings.
            .choice()
                .when(exchangeProperty(TRUST_ALL))
                    .toD(buildUnsecureSslEndpoint(), connectorConfig.getEndpointCacheMaxSize())
                .endChoice()
                .otherwise()
                    .to(ENDPOINT_RESPONSE_TIME_METRIC + TIMER_ACTION_START)
                        .toD("${exchangeProperty." + TARGET_URL + "}", connectorConfig.getEndpointCacheMaxSize())
                    .to(ENDPOINT_RESPONSE_TIME_METRIC + TIMER_ACTION_STOP)
            .end()
            .log(INFO, getClass().getName(), "Sent ${exchangeProperty." + TYPE + ".replace('" + CLOUD_EVENT_TYPE_PREFIX + "', '')} notification " +
                "[orgId=${exchangeProperty." + ORG_ID + "}, historyId=${exchangeProperty." + ID + "}, targetUrl=${exchangeProperty." + TARGET_URL + "}]")
            .to(direct(SUCCESS));
    }

    private HttpEndpointBuilderFactory.HttpEndpointBuilder buildUnsecureSslEndpoint() {
        return https("${exchangeProperty." + TARGET_URL_NO_SCHEME + "}")
            .sslContextParameters(getSslContextParameters())
            .x509HostnameVerifier(NoopHostnameVerifier.INSTANCE);
    }
}
