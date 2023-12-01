package com.redhat.cloud.notifications.connector;

import io.quarkus.arc.DefaultBean;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.CONNECTOR_TO_ENGINE;
import static com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder.KAFKA_REINJECTION;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.KAFKA_REINJECTION_COUNT;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.OUTCOME;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.SUCCESSFUL;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static org.apache.camel.Exchange.ERRORHANDLER_BRIDGE;
import static org.apache.camel.Exchange.EXCEPTION_CAUGHT;
import static org.apache.camel.Exchange.FAILURE_ENDPOINT;
import static org.apache.camel.Exchange.FAILURE_ROUTE_ID;
import static org.apache.camel.Exchange.FATAL_FALLBACK_ERROR_HANDLER;

/**
 * Extend this class in an {@link ApplicationScoped} bean from a connector Maven module to change the
 * behavior of the connector in case of failure while calling an external service (e.g. Slack, Splunk...).
 * If this class is not extended, then the default implementation below will be used.
 */
@DefaultBean
@ApplicationScoped
public class ExceptionProcessor implements Processor {

    private static final String DEFAULT_LOG_MSG = "Message sending failed on %s: [orgId=%s, historyId=%s, targetUrl=%s]";

    @Inject
    ConnectorConfig connectorConfig;

    @Inject
    ProducerTemplate producerTemplate;

    @Override
    public void process(Exchange exchange) {

        Throwable t = exchange.getProperty(EXCEPTION_CAUGHT, Throwable.class);

        exchange.setProperty(SUCCESSFUL, false);
        exchange.setProperty(OUTCOME, t.getMessage());

        process(t, exchange);

        /*
         * There is currently a bug in Camel that will cause a NullPointerException throw when SEDA is used and the
         * exchange is passed to producerTemplate#send. To work around that bug, we're cloning the current exchange
         * and removing all Camel internal properties related to the exception that is being processed.
         */
        Exchange exchangeCopy = exchange.copy();
        exchangeCopy.removeProperty(ERRORHANDLER_BRIDGE);
        exchangeCopy.removeProperty(EXCEPTION_CAUGHT);
        exchangeCopy.removeProperty(FAILURE_ENDPOINT);
        exchangeCopy.removeProperty(FAILURE_ROUTE_ID);
        exchangeCopy.removeProperty(FATAL_FALLBACK_ERROR_HANDLER);

        // Attempt reinjecting the message to the incoming Kafka queue as a way
        // of improving our fault tolerance. After a few attempts we should
        // give up and acknowledge the failure.
        final String route;
        if (exchange.getProperty(KAFKA_REINJECTION_COUNT, 0, Integer.class) < this.connectorConfig.getKafkaMaximumReinjections()) {
            route = String.format("direct:%s", KAFKA_REINJECTION);
        } else {
            route = String.format("direct:%s", CONNECTOR_TO_ENGINE);
        }

        this.producerTemplate.send(route, exchangeCopy);
    }

    protected final void logDefault(Throwable t, Exchange exchange) {
        Log.errorf(
                t,
                DEFAULT_LOG_MSG,
                getRouteId(exchange),
                getOrgId(exchange),
                getExchangeId(exchange),
                getTargetUrl(exchange)
        );
    }

    protected final String getRouteId(Exchange exchange) {
        return exchange.getFromRouteId();
    }

    protected final String getExchangeId(Exchange exchange) {
        return exchange.getProperty(ID, String.class);
    }

    protected final String getOrgId(Exchange exchange) {
        return exchange.getProperty(ORG_ID, String.class);
    }

    protected final String getTargetUrl(Exchange exchange) {
        return exchange.getProperty(TARGET_URL, String.class);
    }

    protected void process(Throwable t, Exchange exchange) {
        logDefault(t, exchange);
    }
}
