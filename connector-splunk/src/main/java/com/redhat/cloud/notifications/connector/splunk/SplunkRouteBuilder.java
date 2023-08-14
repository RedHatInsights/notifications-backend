package com.redhat.cloud.notifications.connector.splunk;

import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;
import org.apache.camel.component.http.HttpComponent;
import org.apache.http.conn.ssl.NoopHostnameVerifier;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static com.redhat.cloud.notifications.connector.splunk.ExchangeProperty.ACCOUNT_ID;
import static com.redhat.cloud.notifications.connector.splunk.ExchangeProperty.AUTHENTICATION_TOKEN;
import static com.redhat.cloud.notifications.connector.splunk.ExchangeProperty.TARGET_URL_NO_SCHEME;
import static com.redhat.cloud.notifications.connector.splunk.ExchangeProperty.TRUST_ALL;
import static com.redhat.cloud.notifications.connector.splunk.SplunkTrustAllManager.getSslContextParameters;
import static org.apache.camel.LoggingLevel.INFO;
import static org.apache.camel.builder.endpoint.dsl.HttpEndpointBuilderFactory.HttpEndpointBuilder;

@ApplicationScoped
public class SplunkRouteBuilder extends EngineToConnectorRouteBuilder {

    @Inject
    SplunkConnectorConfig connectorConfig;

    @Inject
    EventsSplitter eventsSplitter;

    @Override
    public void configureRoute() {

        configureHttpsComponent();

        from(direct(ENGINE_TO_CONNECTOR))
                .routeId(connectorConfig.getConnectorName())
                // Events are split to be sent in batch to Splunk HEC.
                .process(eventsSplitter)
                .setHeader("Authorization", simple("Splunk ${exchangeProperty." + AUTHENTICATION_TOKEN + "}"))
                // SSL certificates may or may not be verified depending on the integration settings.
                .choice()
                .when(exchangeProperty(TRUST_ALL))
                        .toD(buildSplunkEndpoint(true), connectorConfig.getEndpointCacheMaxSize())
                .endChoice()
                .otherwise()
                        .toD(buildSplunkEndpoint(false), connectorConfig.getEndpointCacheMaxSize())
                .end()
                .log(INFO, getClass().getName(), "Delivered event ${exchangeProperty." + ID + "} " +
                        "(orgId ${exchangeProperty." + ORG_ID + "} account ${exchangeProperty." + ACCOUNT_ID + "}) " +
                        "to ${exchangeProperty." + TARGET_URL + "}")
                .to(direct(SUCCESS));
    }

    private void configureHttpsComponent() {
        HttpComponent httpComponent = getCamelContext().getComponent("https", HttpComponent.class);
        httpComponent.setConnectTimeout(connectorConfig.getHttpsConnectTimeout());
        httpComponent.setSocketTimeout(connectorConfig.getHttpsSocketTimeout());
    }

    private HttpEndpointBuilder buildSplunkEndpoint(boolean trustAll) {
        HttpEndpointBuilder endpointBuilder = https("${exchangeProperty." + TARGET_URL_NO_SCHEME + "}").httpMethod("POST");
        if (trustAll) {
            endpointBuilder
                    .sslContextParameters(getSslContextParameters())
                    .x509HostnameVerifier(NoopHostnameVerifier.INSTANCE);
        }
        return endpointBuilder;
    }
}
