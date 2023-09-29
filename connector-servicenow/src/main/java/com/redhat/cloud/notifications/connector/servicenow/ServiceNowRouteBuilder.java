package com.redhat.cloud.notifications.connector.servicenow;

import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.component.http.HttpComponent;
import org.apache.hc.core5.util.Timeout;
import org.apache.http.conn.ssl.NoopHostnameVerifier;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static com.redhat.cloud.notifications.connector.servicenow.ExchangeProperty.ACCOUNT_ID;
import static com.redhat.cloud.notifications.connector.servicenow.ExchangeProperty.TARGET_URL_NO_SCHEME;
import static com.redhat.cloud.notifications.connector.servicenow.ExchangeProperty.TRUST_ALL;
import static com.redhat.cloud.notifications.connector.servicenow.ServiceNowTrustAllManager.getSslContextParameters;
import static org.apache.camel.LoggingLevel.INFO;
import static org.apache.camel.builder.endpoint.dsl.HttpEndpointBuilderFactory.HttpEndpointBuilder;

@ApplicationScoped
public class ServiceNowRouteBuilder extends EngineToConnectorRouteBuilder {

    @Inject
    ServiceNowConnectorConfig connectorConfig;

    @Override
    public void configureRoute() {

        configureHttpsComponent();

        from(seda(ENGINE_TO_CONNECTOR))
            .routeId(connectorConfig.getConnectorName())
            .process(new BasicAuthenticationProcessor())
            // SSL certificates may or may not be verified depending on the integration settings.
            .choice()
            .when(exchangeProperty(TRUST_ALL))
            .toD(buildServiceNowEndpoint(true), connectorConfig.getEndpointCacheMaxSize())
            .endChoice()
            .otherwise()
            .toD(buildServiceNowEndpoint(false), connectorConfig.getEndpointCacheMaxSize())
            .end()
            .log(INFO, getClass().getName(), "Delivered event ${exchangeProperty." + ID + "} " +
                "(orgId ${exchangeProperty." + ORG_ID + "} account ${exchangeProperty." + ACCOUNT_ID + "}) " +
                "to ${exchangeProperty." + TARGET_URL + "}")
            .to(direct(SUCCESS));
    }

    private void configureHttpsComponent() {
        HttpComponent httpComponent = getCamelContext().getComponent("https", HttpComponent.class);
        httpComponent.setConnectTimeout(Timeout.ofMilliseconds(connectorConfig.getHttpsConnectTimeout()));
        httpComponent.setSoTimeout(Timeout.ofMilliseconds(connectorConfig.getHttpsSocketTimeout()));
    }

    private HttpEndpointBuilder buildServiceNowEndpoint(boolean trustAll) {
        HttpEndpointBuilder endpointBuilder = https("${exchangeProperty." + TARGET_URL_NO_SCHEME + "}").httpMethod("POST");
        if (trustAll) {
            endpointBuilder
                .sslContextParameters(getSslContextParameters())
                .x509HostnameVerifier(NoopHostnameVerifier.INSTANCE);
        }
        return endpointBuilder;
    }
}
