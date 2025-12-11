package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.endpoint.dsl.KafkaEndpointBuilderFactory;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;

@ApplicationScoped
public class EmailRouteBuilder extends EngineToConnectorRouteBuilder {

    static final String ROUTE_ID_KAFKA_HIGH_VOLUME_ROUTE = "kafka-high-volume-entrypoint";

    /**
     * Holds all the configuration parameters required to run the connector.
     */
    @Inject
    EmailConnectorConfig emailConnectorConfig;

    @Inject
    EmailManagementProcessor emailManagementProcessor;

    /**
     * Configures the flow for this connector.
     */
    @Override
    public void configureRoutes() {
        // Read events from the high volume topic and forward them to the
        // entrypoint, so that they get processed as usual.
        if (this.emailConnectorConfig.isIncomingKafkaHighVolumeTopicEnabled()) {
            from(this.buildKafkaHighVolumeEndpoint())
                .routeId(ROUTE_ID_KAFKA_HIGH_VOLUME_ROUTE)
                .to(direct(ENTRYPOINT));
        }

        from(seda(ENGINE_TO_CONNECTOR))
            .routeId(emailConnectorConfig.getConnectorName())
            .process(emailManagementProcessor)
            .end()
            .to(direct(SUCCESS));
    }

    /**
     * Builds the Kafka consumer for the high volume Kafka topic.
     * @return the built endpoint for the high volume Kafka consumer.
     */
    private KafkaEndpointBuilderFactory.KafkaEndpointConsumerBuilder buildKafkaHighVolumeEndpoint() {
        return kafka(this.emailConnectorConfig.getIncomingKafkaHighVolumeTopic())
            .groupId(this.emailConnectorConfig.getIncomingKafkaGroupId())
            .maxPollRecords(this.emailConnectorConfig.getIncomingKafkaHighVolumeMaxPollRecords())
            .maxPollIntervalMs(this.emailConnectorConfig.getIncomingKafkaHighVolumeMaxPollIntervalMs())
            .pollOnError(this.emailConnectorConfig.getIncomingKafkaHighVolumePollOnError());
    }
}
