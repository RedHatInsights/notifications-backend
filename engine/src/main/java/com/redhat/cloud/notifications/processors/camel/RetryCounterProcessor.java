package com.redhat.cloud.notifications.processors.camel;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import javax.enterprise.context.ApplicationScoped;

import static com.redhat.cloud.notifications.processors.camel.google.chat.GoogleChatRouteBuilder.GOOGLE_CHAT_ROUTE;
import static com.redhat.cloud.notifications.processors.camel.slack.SlackRouteBuilder.SLACK_ROUTE;
import static com.redhat.cloud.notifications.processors.camel.teams.TeamsRouteBuilder.TEAMS_ROUTE;

@ApplicationScoped
public class RetryCounterProcessor implements Processor {

    public static final String CAMEL_SLACK_RETRY_COUNTER = "camel.slack.retry.counter";
    public static final String CAMEL_TEAMS_RETRY_COUNTER = "camel.teams.retry.counter";
    public static final String CAMEL_GOOGLE_CHAT_RETRY_COUNTER = "camel.google.chat.retry.counter";

    MeterRegistry registry;
    Counter slackRetryCounter;
    Counter teamsRetryCounter;
    Counter googleChatRetryCounter;

    public RetryCounterProcessor(MeterRegistry registry) {
        this.registry = registry;
        slackRetryCounter = registry.counter(CAMEL_SLACK_RETRY_COUNTER);
        teamsRetryCounter = registry.counter(CAMEL_TEAMS_RETRY_COUNTER);
        googleChatRetryCounter = registry.counter(CAMEL_GOOGLE_CHAT_RETRY_COUNTER);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final String routeId = exchange.getFromRouteId();
        switch (routeId) {
            case SLACK_ROUTE:
                slackRetryCounter.increment();
                break;
            case TEAMS_ROUTE:
                teamsRetryCounter.increment();
                break;
            case GOOGLE_CHAT_ROUTE:
                googleChatRetryCounter.increment();
                break;
            default:
                Log.warnf("Unsupported route Id: %s", routeId);
                break;
        }
    }
}
