package com.redhat.cloud.notifications.processors.camel.slack;

import com.redhat.cloud.notifications.processors.camel.CamelRouteBuilder;
import com.redhat.cloud.notifications.processors.camel.IncomingCloudEventFilter;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static com.redhat.cloud.notifications.events.EndpointProcessor.SLACK_ENDPOINT_SUBTYPE;
import static com.redhat.cloud.notifications.processors.ConnectorSender.CLOUD_EVENT_TYPE_PREFIX;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.OUTCOME;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.SUCCESSFUL;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.WEBHOOK_URL;
import static com.redhat.cloud.notifications.processors.camel.ReturnRouteBuilder.RETURN_ROUTE_NAME;

@ApplicationScoped
public class SlackRouteBuilder extends CamelRouteBuilder {

    public static final String SLACK_ROUTE = "slack";

    private static final String KAFKA_GROUP_ID = "notifications-connector-slack";

    @Inject
    SlackNotificationProcessor slackNotificationProcessor;

    @Override
    public void configure() {

        configureCommonExceptionHandler();

        from(kafka(toCamelTopic).groupId(KAFKA_GROUP_ID))
                .routeId(SLACK_ROUTE)
                .filter(new IncomingCloudEventFilter(CLOUD_EVENT_TYPE_PREFIX + SLACK_ENDPOINT_SUBTYPE))
                .process(slackNotificationProcessor)
                .toD(slack("${exchangeProperty.channel}").webhookUrl("${exchangeProperty." + WEBHOOK_URL + "}"), maxEndpointCacheSize)
                .setProperty(SUCCESSFUL, constant(true))
                .setProperty(OUTCOME, simple("Event ${exchangeProperty." + ID + "} sent successfully"))
                .to(direct(RETURN_ROUTE_NAME));
    }
}
