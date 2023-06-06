package com.redhat.cloud.notifications.connector;

import org.apache.camel.builder.endpoint.EndpointRouteBuilder;

import javax.inject.Inject;

import static org.apache.camel.LoggingLevel.DEBUG;

public abstract class EngineToConnectorRouteBuilder extends EndpointRouteBuilder {

    public static final String ENGINE_TO_CONNECTOR = "engine-to-connector";
    public static final String CAMEL_HTTP_HEADERS_PATTERN = "CamelHttp*";

    @Inject
    ConnectorConfig connectorConfig;

    @Inject
    IncomingCloudEventFilter incomingCloudEventFilter;

    @Inject
    IncomingCloudEventProcessor incomingCloudEventProcessor;

    @Inject
    RedeliveryCounterProcessor redeliveryCounterProcessor;

    @Inject
    ExceptionProcessor exceptionProcessor;

    @Inject
    RedeliveryPredicate redeliveryPredicate;

    @Override
    public void configure() {

        onException(Throwable.class)
                .onWhen(redeliveryPredicate::matches)
                .handled(true)
                .maximumRedeliveries(connectorConfig.getRedeliveryMaxAttempts())
                .redeliveryDelay(connectorConfig.getRedeliveryDelay())
                .retryAttemptedLogLevel(DEBUG)
                .onRedelivery(redeliveryCounterProcessor)
                .process(exceptionProcessor);

        onException(Throwable.class)
                .onWhen(redeliveryPredicate::doesNotMatch)
                .handled(true)
                .process(exceptionProcessor);

        from(kafka(connectorConfig.getIncomingKafkaTopic()).groupId(connectorConfig.getIncomingKafkaGroupId()))
                .routeId(ENGINE_TO_CONNECTOR)
                .to(log(getClass().getName()).level("DEBUG").showHeaders(true).showBody(true))
                .filter(incomingCloudEventFilter)
                .process(incomingCloudEventProcessor)
                .to(log(getClass().getName()).level("DEBUG").showProperties(true))
                .to(direct(ENGINE_TO_CONNECTOR));

        configureRoute();
    }

    public abstract void configureRoute();
}
