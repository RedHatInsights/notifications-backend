package com.redhat.cloud.notifications.connector;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.REDELIVERY_ATTEMPTS;

@ApplicationScoped
public class RedeliveryProcessor implements Processor {

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    ConnectorConfig connectorConfig;

    private Counter counter;

    @PostConstruct
    void postConstruct() {
        counter = meterRegistry.counter(connectorConfig.getRedeliveryCounterName());
    }

    @Override
    public void process(Exchange exchange) {
        counter.increment();
        exchange.setProperty(REDELIVERY_ATTEMPTS, exchange.getProperty(REDELIVERY_ATTEMPTS, 0, int.class) + 1);
    }
}
