package com.redhat.cloud.notifications.processors.slack;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import javax.enterprise.context.ApplicationScoped;

import static com.redhat.cloud.notifications.processors.slack.SlackRouteBuilder.SLACK_INCOMING_ROUTE;

@ApplicationScoped
public class RetryCounterProcessor implements Processor {

    public static final String CAMEL_SLACK_RETRY_COUNTER = "camel.slack.retry.counter";
    MeterRegistry registry;
    Counter slackRetryCounter;

    public RetryCounterProcessor(MeterRegistry registry) {
        this.registry = registry;
        slackRetryCounter = registry.counter(CAMEL_SLACK_RETRY_COUNTER);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final String routeId = exchange.getFromRouteId();
        switch (routeId) {
            case SLACK_INCOMING_ROUTE :
                slackRetryCounter.increment();
                break;
            default:
                Log.warnf("Unsupported route Id: %s", routeId);
                break;
        }
    }
}
