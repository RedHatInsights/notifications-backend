package com.redhat.cloud.notifications.connector;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class RedeliveryCounterProcessor implements Processor {

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
    }
}
