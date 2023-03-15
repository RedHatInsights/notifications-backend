package com.redhat.cloud.notifications.processors.slack;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static org.apache.camel.LoggingLevel.ERROR;

@ApplicationScoped
public class SlackRouteBuilder extends RouteBuilder {

    public static final String REST_PATH = API_INTERNAL + "/slack";

    private static final String DIRECT_ENDPOINT_NAME = "direct:slack";

    @ConfigProperty(name = "notifications.slack.camel.maximum-redeliveries", defaultValue = "3")
    int maxRedeliveries;

    @ConfigProperty(name = "notifications.slack.camel.redelivery-delay", defaultValue = "1")
    long redeliveryDelay;

    @ConfigProperty(name = "notifications.slack.camel.log-retry-attempted", defaultValue = "true")
    boolean logRetryAttempted;

    @ConfigProperty(name = "notifications.slack.camel.max-endpoint-cache-size", defaultValue = "100")
    int maxEndpointCacheSize;

    @Inject
    SlackNotificationProcessor slackNotificationProcessor;

    @Override
    public void configure() {

        onException(CamelExchangeException.class)
                .maximumRedeliveries(maxRedeliveries)
                .redeliveryDelay(redeliveryDelay)
                .logRetryAttempted(logRetryAttempted)
                .log(ERROR, "Could not send message to webhookUrl=${header.webhookUrl} channel=${header.channel}: ${exception.message}");

        rest(REST_PATH)
                .post()
                .consumes("application/json")
                .to(DIRECT_ENDPOINT_NAME);

        from(DIRECT_ENDPOINT_NAME)
                .process(slackNotificationProcessor)
                .toD("slack:${header.channel}?webhookUrl=${header.webhookUrl}", maxEndpointCacheSize);
    }
}
