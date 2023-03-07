package com.redhat.cloud.notifications.processors.slack;

import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class SlackRouteBuilder extends RouteBuilder {

    public static final String REST_PATH = "/internal/slack";

    private static final String DIRECT_ENDPOINT_NAME = "direct:slack";

    @ConfigProperty(name = "notifications.slack.camel.max-endpoint-cache-size", defaultValue = "100")
    int maxEndpointCacheSize;

    @Inject
    SlackNotificationProcessor slackNotificationProcessor;

    @Override
    public void configure() {

        rest(REST_PATH)
                .post()
                .consumes("application/json")
                .to(DIRECT_ENDPOINT_NAME);

        from(DIRECT_ENDPOINT_NAME)
                .process(slackNotificationProcessor)
                .toD("slack:${header.channel}?webhookUrl=${header.webhookUrl}", maxEndpointCacheSize);
    }
}
