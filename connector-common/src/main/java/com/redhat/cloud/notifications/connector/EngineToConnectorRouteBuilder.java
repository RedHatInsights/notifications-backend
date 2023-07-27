package com.redhat.cloud.notifications.connector;

import org.apache.camel.builder.endpoint.EndpointRouteBuilder;

import javax.inject.Inject;

import static org.apache.camel.LoggingLevel.DEBUG;
import static org.apache.camel.builder.endpoint.dsl.KafkaEndpointBuilderFactory.KafkaEndpointConsumerBuilder;

public abstract class EngineToConnectorRouteBuilder extends EndpointRouteBuilder {

    public static final String ENGINE_TO_CONNECTOR = "engine-to-connector";

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

        from(buildKafkaEndpoint())
                .routeId(ENGINE_TO_CONNECTOR)
                .to(log(getClass().getName()).level("DEBUG").showHeaders(true).showBody(true))
                .filter(incomingCloudEventFilter)
                // Headers coming from Kafka must not be forwarded to external services.
                .removeHeaders("*")
                .process(incomingCloudEventProcessor)
                .to(log(getClass().getName()).level("DEBUG").showProperties(true))
                .to(direct(ENGINE_TO_CONNECTOR));

        configureRoute();
    }

    public abstract void configureRoute();

    private KafkaEndpointConsumerBuilder buildKafkaEndpoint() {
        return kafka(connectorConfig.getIncomingKafkaTopic())
                .groupId(connectorConfig.getIncomingKafkaGroupId())
                .maxPollRecords(connectorConfig.getIncomingKafkaMaxPollRecords())
                .maxPollIntervalMs(connectorConfig.getIncomingKafkaMaxPollIntervalMs())
                .pollOnError(connectorConfig.getIncomingKafkaPollOnError());
    }
}
