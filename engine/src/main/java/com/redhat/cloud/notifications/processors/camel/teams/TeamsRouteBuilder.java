package com.redhat.cloud.notifications.processors.camel.teams;

import com.redhat.cloud.notifications.processors.camel.CamelRouteBuilder;
import com.redhat.cloud.notifications.processors.camel.IncomingCloudEventFilter;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static com.redhat.cloud.notifications.events.EndpointProcessor.TEAMS_ENDPOINT_SUBTYPE;
import static com.redhat.cloud.notifications.models.HttpType.POST;
import static com.redhat.cloud.notifications.processors.ConnectorSender.CLOUD_EVENT_TYPE_PREFIX;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.OUTCOME;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.SUCCESSFUL;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.WEBHOOK_URL;
import static com.redhat.cloud.notifications.processors.camel.ReturnRouteBuilder.RETURN_ROUTE_NAME;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_METHOD;

@ApplicationScoped
public class TeamsRouteBuilder extends CamelRouteBuilder {

    public static final String TEAMS_ROUTE = "microsoft-teams";

    private static final String KAFKA_GROUP_ID = "notifications-connector-microsoft-teams";

    @Inject
    TeamsNotificationProcessor teamsNotificationProcessor;

    @Override
    public void configure() {

        configureCommonExceptionHandler();

        from(kafka(toCamelTopic).groupId(KAFKA_GROUP_ID))
                .routeId(TEAMS_ROUTE)
                .filter(new IncomingCloudEventFilter(CLOUD_EVENT_TYPE_PREFIX + TEAMS_ENDPOINT_SUBTYPE))
                .process(teamsNotificationProcessor)
                .removeHeaders(CAMEL_HTTP_HEADERS_PATTERN)
                .setHeader(HTTP_METHOD, constant(POST))
                .setHeader(CONTENT_TYPE, constant(APPLICATION_JSON))
                .toD("${exchangeProperty." + WEBHOOK_URL + "}", maxEndpointCacheSize)
                .setProperty(SUCCESSFUL, constant(true))
                .setProperty(OUTCOME, simple("Event ${exchangeProperty." + ID + "} sent successfully"))
                .to(direct(RETURN_ROUTE_NAME));
    }
}
