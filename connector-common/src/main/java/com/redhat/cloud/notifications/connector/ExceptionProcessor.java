package com.redhat.cloud.notifications.connector;

import io.quarkus.arc.DefaultBean;
import io.quarkus.logging.Log;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.CONNECTOR_TO_ENGINE;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.OUTCOME;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.SUCCESSFUL;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static org.apache.camel.Exchange.EXCEPTION_CAUGHT;

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
    ProducerTemplate producerTemplate;

    @Override
    public void process(Exchange exchange) {

        Throwable t = exchange.getProperty(EXCEPTION_CAUGHT, Throwable.class);

        exchange.setProperty(SUCCESSFUL, false);
        exchange.setProperty(OUTCOME, t.getMessage());

        log(t, exchange);

        producerTemplate.send("direct:" + CONNECTOR_TO_ENGINE, exchange);
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

    protected void log(Throwable t, Exchange exchange) {
        logDefault(t, exchange);
    }
}
