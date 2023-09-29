package com.redhat.cloud.notifications.connector;

import jakarta.inject.Inject;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.component.seda.SedaComponent;

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
    public void configure() throws Exception {

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

        if (connectorConfig.isSedaEnabled()) {
            configureSedaComponent();
        }

        from(buildKafkaEndpoint())
                .routeId(ENGINE_TO_CONNECTOR)
                .to(log(getClass().getName()).level("DEBUG").showHeaders(true).showBody(true))
                .filter(incomingCloudEventFilter)
                // Headers coming from Kafka must not be forwarded to external services.
                .removeHeaders("*")
                .process(incomingCloudEventProcessor)
                .to(log(getClass().getName()).level("DEBUG").showProperties(true))
                // TODO The following lines should be removed when all connectors are migrated to SEDA.
                .choice()
                .when(exchange -> connectorConfig.isSedaEnabled())
                .to(seda(ENGINE_TO_CONNECTOR))
                .endChoice()
                .otherwise()
                .to(direct(ENGINE_TO_CONNECTOR))
                .end();

        configureRoute();
    }

    public abstract void configureRoute() throws Exception;

    private KafkaEndpointConsumerBuilder buildKafkaEndpoint() {
        return kafka(connectorConfig.getIncomingKafkaTopic())
                .groupId(connectorConfig.getIncomingKafkaGroupId())
                .maxPollRecords(connectorConfig.getIncomingKafkaMaxPollRecords())
                .maxPollIntervalMs(connectorConfig.getIncomingKafkaMaxPollIntervalMs())
                .pollOnError(connectorConfig.getIncomingKafkaPollOnError());
    }

    private void configureSedaComponent() {
        SedaComponent component = getContext().getComponent("seda", SedaComponent.class);
        component.setConcurrentConsumers(connectorConfig.getSedaConcurrentConsumers());
        component.setQueueSize(connectorConfig.getSedaQueueSize());
        // The Kafka messages consumption is blocked (paused) when the SEDA queue is full.
        component.setDefaultBlockWhenFull(true);
        // The onException clauses will work with SEDA only if this is set to true.
        component.setBridgeErrorHandler(true);
    }
}
