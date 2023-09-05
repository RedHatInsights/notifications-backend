package com.redhat.cloud.notifications.connector.webhook;

import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;
import com.redhat.cloud.notifications.connector.webhook.authentication.BasicAuthenticationProcessor;
import com.redhat.cloud.notifications.connector.webhook.authentication.BearerTokenAuthenticationProcessor;
import com.redhat.cloud.notifications.connector.webhook.authentication.InsightsTokenAuthenticationProcessor;
import org.apache.camel.builder.endpoint.dsl.HttpEndpointBuilderFactory;
import org.apache.camel.component.http.HttpComponent;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TYPE;
import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.BASIC_AUTH_USERNAME;
import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.BEARER_TOKEN;
import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.INSIGHT_TOKEN_HEADER;
import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.TARGET_URL_NO_SCHEME;
import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.TRUST_ALL;
import static com.redhat.cloud.notifications.connector.webhook.SslTrustAllManager.getSslContextParameters;
import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.LoggingLevel.INFO;


@ApplicationScoped
public class WebhookRouteBuilder extends EngineToConnectorRouteBuilder {

    public static final String CLOUD_EVENT_TYPE_PREFIX = "com.redhat.console.notification.toCamel.";
    private static final String APPLICATION_JSON = "application/json";

    @Inject
    WebhookConnectorConfig webhookConnectorConfig;

    @Override
    public void configureRoute() {

        configureTimeout(getContext().getComponent("http", HttpComponent.class));
        configureTimeout(getContext().getComponent("https", HttpComponent.class));

        from(direct(ENGINE_TO_CONNECTOR))
            .setHeader(CONTENT_TYPE, constant(APPLICATION_JSON))
            .routeId(webhookConnectorConfig.getConnectorName())
            .choice()
                .when(exchangeProperty(INSIGHT_TOKEN_HEADER))
                    .process(new InsightsTokenAuthenticationProcessor())
                .endChoice()
                .when(exchangeProperty(BASIC_AUTH_USERNAME))
                    .process(new BasicAuthenticationProcessor())
                .endChoice()
                .when(exchangeProperty(BEARER_TOKEN))
                    .process(new BearerTokenAuthenticationProcessor())
                .endChoice()
            .end()
            // SSL certificates may or may not be verified depending on the integration settings.
            .choice()
                .when(exchangeProperty(TRUST_ALL))
                    .toD(buildUnsecureSslEndpoint(), webhookConnectorConfig.getEndpointCacheMaxSize())
                .endChoice()
                .otherwise()
                    .toD("${exchangeProperty." + TARGET_URL + "}", webhookConnectorConfig.getEndpointCacheMaxSize())
            .end()
            .log(INFO, getClass().getName(), "Sent ${exchangeProperty." + TYPE + ".replace('" + CLOUD_EVENT_TYPE_PREFIX + "', '')} notification " +
                "[orgId=${exchangeProperty." + ORG_ID + "}, historyId=${exchangeProperty." + ID + "}, targetUrl=${exchangeProperty." + TARGET_URL + "}]")
            .to(direct(SUCCESS));
    }

    private void configureTimeout(HttpComponent httpComponent) {
        httpComponent.setConnectTimeout(webhookConnectorConfig.getHttpsConnectTimeout());
        httpComponent.setSocketTimeout(webhookConnectorConfig.getHttpsSocketTimeout());
    }

    private HttpEndpointBuilderFactory.HttpEndpointBuilder buildUnsecureSslEndpoint() {
        return https("${exchangeProperty." + TARGET_URL_NO_SCHEME + "}")
            .sslContextParameters(getSslContextParameters())
            .x509HostnameVerifier(NoopHostnameVerifier.INSTANCE);
    }
}
