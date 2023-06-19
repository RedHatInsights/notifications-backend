package com.redhat.cloud.notifications.connector;

import org.apache.camel.builder.endpoint.EndpointRouteBuilder;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.OUTCOME;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.SUCCESSFUL;

@ApplicationScoped
public class ConnectorToEngineRouteBuilder extends EndpointRouteBuilder {

    public static final String SUCCESS = "success";
    public static final String CONNECTOR_TO_ENGINE = "connector-to-engine";

    @Inject
    ConnectorConfig connectorConfig;

    @Inject
    OutgoingCloudEventBuilder outgoingCloudEventBuilder;

    @Override
    public void configure() {

        from(direct(SUCCESS))
                .routeId(SUCCESS)
                .setProperty(SUCCESSFUL, constant(true))
                .setProperty(OUTCOME, simple("Event ${exchangeProperty." + ID + "} sent successfully"))
                .to(direct(CONNECTOR_TO_ENGINE));

        from(direct(CONNECTOR_TO_ENGINE))
                .routeId(CONNECTOR_TO_ENGINE)
                .process(outgoingCloudEventBuilder)
                .to(log(getClass().getName()).level("DEBUG").showHeaders(true).showBody(true))
                .to(kafka(connectorConfig.getOutgoingKafkaTopic()));
    }
}
