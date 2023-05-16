package com.redhat.cloud.notifications.processors.camel.slack;

import com.redhat.cloud.notifications.processors.camel.CamelCommonExceptionHandler;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@ApplicationScoped
public class SlackRouteBuilder extends CamelCommonExceptionHandler {

    public static final String REST_PATH = API_INTERNAL + "/slack";
    public static final String SLACK_OUTGOING_ROUTE = "slack-outgoing";
    public static final String SLACK_INCOMING_ROUTE = "slack-incoming";
    private static final String SLACK_DIRECT_ENDPOINT = "direct:slack";

    @Inject
    SlackNotificationProcessor slackNotificationProcessor;

    @Override
    public void configure() {

        configureCommonExceptionHandler();

        /*
         * This route exposes a REST endpoint that is used from SlackProcessor to send a Slack notification.
         */
        rest(REST_PATH)
                .post()
                .consumes(APPLICATION_JSON)
                .routeId(SLACK_INCOMING_ROUTE)
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
