package com.redhat.cloud.notifications.connector;

import com.redhat.cloud.notifications.connector.payload.PayloadDetailsRequestPreparer;
import com.redhat.cloud.notifications.connector.payload.PayloadDetailsResponseProcessor;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.KAFKA_REINJECTION_DELAY;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static org.apache.camel.LoggingLevel.DEBUG;
import static org.apache.camel.LoggingLevel.INFO;
import static org.apache.camel.builder.endpoint.dsl.KafkaEndpointBuilderFactory.KafkaEndpointConsumerBuilder;

public abstract class EngineToConnectorRouteBuilder extends EndpointRouteBuilder {

    public static final String ENGINE_TO_CONNECTOR = "engine-to-connector";
    public static final String KAFKA_REINJECTION = "kafka-reinjection";

    @Inject
    ConnectorConfig connectorConfig;

    @Inject
    IncomingCloudEventFilter incomingCloudEventFilter;

    @Inject
    IncomingCloudEventProcessor incomingCloudEventProcessor;

    /**
     * Responsible for extracting the reinjection count from the Kafka header,
     * if it is present.
     */
    @Inject
    IncomingKafkaReinjectionHeadersProcessor incomingKafkaReinjectionHeadersProcessor;

    @Inject
    KafkaReinjectionProcessor kafkaReinjectionProcessor;

    @Inject
    RedeliveryProcessor redeliveryProcessor;

    @Inject
    ExceptionProcessor exceptionProcessor;

    @Inject
    PayloadDetailsRequestPreparer payloadDetailsRequestPreparer;

    @Inject
    PayloadDetailsResponseProcessor payloadDetailsResponseProcessor;

    @Inject
    RedeliveryPredicate redeliveryPredicate;

    @Inject
    Instance<CamelComponentConfigurator> camelComponentsConfigurators;

    @Override
    public void configure() throws Exception {

        // Camel components must be configured before they are included in Camel routes definitions.
        configureComponents();

        onException(Throwable.class)
                .onWhen(redeliveryPredicate::matches)
                .handled(true)
                .maximumRedeliveries(connectorConfig.getRedeliveryMaxAttempts())
                .redeliveryDelay(connectorConfig.getRedeliveryDelay())
                .retryAttemptedLogLevel(DEBUG)
                .onRedelivery(redeliveryProcessor)
                .process(exceptionProcessor);

        onException(Throwable.class)
                .onWhen(redeliveryPredicate::doesNotMatch)
                .handled(true)
                .process(exceptionProcessor);

        from(buildKafkaEndpoint())
                .routeId(ENGINE_TO_CONNECTOR)
                .to(log(getClass().getName()).level("DEBUG").showHeaders(true).showBody(true))
                .filter(incomingCloudEventFilter)
                .process(this.incomingKafkaReinjectionHeadersProcessor)
                .choice()
                    .when(header(Constants.X_RH_NOTIFICATIONS_CONNECTOR_PAYLOAD_HEADER).isNotNull())
                        .process(this.payloadDetailsRequestPreparer)
                        .to(http(this.connectorConfig.getNotificationsEngineHostname()))
                        .process(this.payloadDetailsResponseProcessor)
                .end()
                // Headers coming from Kafka must not be forwarded to external services.
                .removeHeaders("*")
                .process(incomingCloudEventProcessor)
                .to(log(getClass().getName()).level("DEBUG").showProperties(true))
                .to(seda(ENGINE_TO_CONNECTOR));

        /*
         * Defines a route to reinject the messages to the incoming queue,
         * after determining a proper delay. The delay for the reinjection is
         * asynchronously applied, so that we do not block the thread waiting.
         */
        from(direct(KAFKA_REINJECTION))
            .routeId(KAFKA_REINJECTION)
            .process(this.kafkaReinjectionProcessor)
            .delay(simpleF("${exchangeProperty.%s}", KAFKA_REINJECTION_DELAY))
                .asyncDelayed()
            .end()
            .log(INFO, this.getClass().getName(), "[orgId=${exchangeProperty." + ORG_ID + "}, historyId=${exchangeProperty." + ID + "}, delay=${exchangeProperty." + KAFKA_REINJECTION_DELAY + "}] Message reinjected to Kafka")
            .to(kafka(this.connectorConfig.getIncomingKafkaTopic()));

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
