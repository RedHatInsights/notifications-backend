package com.redhat.cloud.notifications.processors.slack;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import java.io.IOException;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static org.apache.camel.LoggingLevel.ERROR;
import static org.apache.camel.LoggingLevel.INFO;

@ApplicationScoped
public class SlackRouteBuilder extends RouteBuilder {

    public static final String REST_PATH = API_INTERNAL + "/slack";
    public static final String SLACK_OUTGOING_ROUTE = "slack-outgoing";

    private static final String SLACK_DIRECT_ENDPOINT = "direct:slack";
    private static final String EXCEPTION_DIRECT_ENDPOINT = "direct:exception";
    private static final String ERROR_MSG = "Slack message sending failed [orgId=${exchangeProperty.orgId}, " +
            "historyId=${exchangeProperty.historyId}, webhookUrl=${exchangeProperty.webhookUrl}, " +
            "channel=${exchangeProperty.channel}]\n${exception.stacktrace}";

    @ConfigProperty(name = "notifications.slack.camel.maximum-redeliveries", defaultValue = "2")
    int maxRedeliveries;

    @ConfigProperty(name = "notifications.slack.camel.redelivery-delay", defaultValue = "1000")
    long redeliveryDelay;

    @ConfigProperty(name = "notifications.slack.camel.max-endpoint-cache-size", defaultValue = "100")
    int maxEndpointCacheSize;

    @Inject
    SlackNotificationProcessor slackNotificationProcessor;

    @Override
    public void configure() {

        /*
         * An IOException can be thrown in case of network issue or of unexpected remote server (Slack) failure.
         * It is worth retrying in that case. Retry attempts will be logged. The exception itself will also be
         * logged eventually if none of the retry attempts were successful.
         */
        onException(IOException.class)
                .maximumRedeliveries(maxRedeliveries)
                .redeliveryDelay(redeliveryDelay)
                .retryAttemptedLogLevel(INFO)
                .to(EXCEPTION_DIRECT_ENDPOINT)
                .handled(true); // The exception won't bubble-up to the caller.

        /*
         * We only saw a CamelExchangeException so far because of a misconfiguration of a Slack integration.
         * Let's treat that like an HTTP 4xx error for now, which implies no retry. It will still be logged.
         */
        onException(CamelExchangeException.class)
                .to(EXCEPTION_DIRECT_ENDPOINT);

        /*
         * This route simply logs exceptions with more details than what Camel provides by default.
         */
        from(EXCEPTION_DIRECT_ENDPOINT)
                .routeId("exception-logging")
                .log(ERROR, ERROR_MSG);

        /*
         * This route exposes a REST endpoint that is used from SlackProcessor to send a Slack notification.
         */
        rest(REST_PATH)
                .post()
                .consumes("application/json")
                .routeId("slack-incoming")
                .to(SLACK_DIRECT_ENDPOINT);

        /*
         * This route transforms an incoming REST payload into a message that is eventually sent to Slack.
         */
        from(SLACK_DIRECT_ENDPOINT)
                .routeId(SLACK_OUTGOING_ROUTE)
                .process(slackNotificationProcessor)
                .toD("slack:${exchangeProperty.channel}?webhookUrl=${exchangeProperty.webhookUrl}", maxEndpointCacheSize);
    }
}
