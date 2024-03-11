package com.redhat.cloud.notifications.connector.email.processors;

import com.redhat.cloud.notifications.connector.ConnectorConfig;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import java.util.HashSet;
import java.util.Set;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.CONNECTOR_TO_ENGINE;
import static com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder.KAFKA_REINJECTION;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.*;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.RECIPIENTS_WITH_EMAIL_ERROR;

/**
 * Extend this class in an {@link ApplicationScoped} bean from a connector Maven module to change the
 * behavior of the connector in case of failure while calling an external service (e.g. Slack, Splunk...).
 * If this class is not extended, then the default implementation below will be used.
 */
@ApplicationScoped
public class SelectOutputProcessor implements Processor {

    @Inject
    ConnectorConfig connectorConfig;

    @Inject
    ProducerTemplate producerTemplate;


    @Override
    public void process(Exchange exchange) {

        final String route;
        if (!exchange.getProperty(RECIPIENTS_WITH_EMAIL_ERROR, new HashSet(), Set.class).isEmpty() &&
            exchange.getProperty(KAFKA_REINJECTION_COUNT, 0, Integer.class) < this.connectorConfig.getKafkaMaximumReinjections()) {
            route = String.format("direct:%s", KAFKA_REINJECTION);
        } else {
            route = String.format("direct:%s", CONNECTOR_TO_ENGINE);
        }
        Log.info("Selected route: " + route);
        this.producerTemplate.send(route, exchange);
    }

}
