package com.redhat.cloud.notifications.connector.email.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.RECIPIENTS_SIZE;

@ApplicationScoped
public class EmailMetricsProcessor implements Processor {

    @Inject
    MeterRegistry meterRegistry;

    private Counter requestsCounter;
    private Counter recipientsCounter;

    @PostConstruct
    void postConstruct() {
        requestsCounter = meterRegistry.counter("notifications.connector.email.requests");
        recipientsCounter = meterRegistry.counter("notifications.connector.email.recipients");
    }

    @Override
    public void process(Exchange exchange) {
        requestsCounter.increment();
        int recipientsSize = exchange.getProperty(RECIPIENTS_SIZE, 0, int.class);
        recipientsCounter.increment(recipientsSize);
    }
}
