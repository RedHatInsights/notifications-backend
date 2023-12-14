package com.redhat.cloud.notifications.connector;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;

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

    @Inject
    ContinueOnErrorPredicate continueOnErrorPredicate;

    @Inject
    Instance<CamelComponentConfigurator> camelComponentsConfigurators;

    @Override
    public void configure() throws Exception {

        // Camel components must be configured before they are included in Camel routes definitions.
        configureComponents();

        onException(Throwable.class)
                .onWhen(redeliveryPredicate::matches)
                .onWhen(continueOnErrorPredicate::matches)
                .maximumRedeliveries(connectorConfig.getRedeliveryMaxAttempts())
                .redeliveryDelay(connectorConfig.getRedeliveryDelay())
                .retryAttemptedLogLevel(DEBUG)
                .onRedelivery(redeliveryCounterProcessor)
                .process(exceptionProcessor)
                .continued(true);

        onException(Throwable.class)
                .onWhen(redeliveryPredicate::matches)
                .maximumRedeliveries(connectorConfig.getRedeliveryMaxAttempts())
                .redeliveryDelay(connectorConfig.getRedeliveryDelay())
                .retryAttemptedLogLevel(DEBUG)
                .onRedelivery(redeliveryCounterProcessor)
                .process(exceptionProcessor)
                .handled(true);

        onException(Throwable.class)
                .process(exceptionProcessor)
                .handled(true);

        from(buildKafkaEndpoint())
                .routeId(ENGINE_TO_CONNECTOR)
                .to(log(getClass().getName()).level("DEBUG").showHeaders(true).showBody(true))
                .filter(incomingCloudEventFilter)
                // Headers coming from Kafka must not be forwarded to external services.
                .removeHeaders("*")
                .process(incomingCloudEventProcessor)
                .to(log(getClass().getName()).level("DEBUG").showProperties(true))
                .to(seda(ENGINE_TO_CONNECTOR));

        configureRoutes();
    }

    private void configureComponents() {
        // All CDI beans implementing CamelComponentConfigurator are retrieved from the CDI container.
        for (CamelComponentConfigurator configurator : camelComponentsConfigurators) {
            // Each one of them is executed.
            configurator.configure(getContext());
            // Then destroyed because it's not needed anymore and would use memory for nothing otherwise.
            camelComponentsConfigurators.destroy(configurator);
        }
    }

    protected abstract void configureRoutes() throws Exception;

    private KafkaEndpointConsumerBuilder buildKafkaEndpoint() {
        return kafka(connectorConfig.getIncomingKafkaTopic())
                .groupId(connectorConfig.getIncomingKafkaGroupId())
                .maxPollRecords(connectorConfig.getIncomingKafkaMaxPollRecords())
                .maxPollIntervalMs(connectorConfig.getIncomingKafkaMaxPollIntervalMs())
                .pollOnError(connectorConfig.getIncomingKafkaPollOnError());
    }
}
