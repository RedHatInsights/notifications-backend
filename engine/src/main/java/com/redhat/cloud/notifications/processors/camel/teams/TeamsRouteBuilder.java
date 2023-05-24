package com.redhat.cloud.notifications.processors.camel.teams;

import com.redhat.cloud.notifications.processors.camel.CamelCommonExceptionHandler;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.models.HttpType.POST;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.OUTCOME;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.SUCCESSFUL;
import static com.redhat.cloud.notifications.processors.camel.ReturnRouteBuilder.RETURN_ROUTE_NAME;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.apache.camel.builder.endpoint.StaticEndpointBuilders.direct;

@ApplicationScoped
public class TeamsRouteBuilder extends CamelCommonExceptionHandler {

    public static final String REST_PATH = API_INTERNAL + "/teams";
    public static final String TEAMS_OUTGOING_ROUTE = "teams-outgoing";

    public static final String TEAMS_INCOMING_ROUTE = "teams-incoming";

    private static final String TEAMS_DIRECT_ENDPOINT = "direct:teams";

    @Inject
    TeamsNotificationProcessor teamsNotificationProcessor;

    @Override
    public void configure() {

        configureCommonExceptionHandler();

        /*
         * This route exposes a REST endpoint that is used from TeamsProcessor to send a Teams notification.
         */
        rest(REST_PATH)
                .post()
                .consumes(APPLICATION_JSON)
                .routeId(TEAMS_INCOMING_ROUTE)
                .to(TEAMS_DIRECT_ENDPOINT);

        /*
         * This route transforms an incoming REST payload into a message that is eventually sent to Teams.
         */
        from(TEAMS_DIRECT_ENDPOINT)
                .routeId(TEAMS_OUTGOING_ROUTE)
                .process(teamsNotificationProcessor)
                .removeHeaders(CAMEL_HTTP_HEADERS_PATTERN)
                .setHeader(HTTP_METHOD, constant(POST))
                .setHeader(CONTENT_TYPE, constant(APPLICATION_JSON))
                .toD("${exchangeProperty.webhookUrl}", maxEndpointCacheSize)
                .setProperty(SUCCESSFUL, constant(true))
                .setProperty(OUTCOME, simple("Event ${exchangeProperty.historyId} sent successfully"))
                .to(direct(RETURN_ROUTE_NAME));
    }
}
